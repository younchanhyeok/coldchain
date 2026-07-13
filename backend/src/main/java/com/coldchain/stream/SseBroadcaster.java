package com.coldchain.stream;

import com.coldchain.alert.event.AlertRaisedEvent;
import com.coldchain.common.GeoPoints;
import com.coldchain.detection.event.AnomalyDetectedEvent;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.prediction.event.PredictionChangedEvent;
import com.coldchain.stream.dto.AlertStreamEvent;
import com.coldchain.stream.dto.AnomalyStreamEvent;
import com.coldchain.stream.dto.BreachStreamEvent;
import com.coldchain.stream.dto.PredictionStreamEvent;
import com.coldchain.stream.dto.ReadingStreamEvent;
import com.coldchain.tracker.domain.TrackerStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 화주별로 emitter를 나눠 자기 트래커의 이벤트만 받게 한다(M5) — 이전엔 로그인만 하면 전
 * 화주의 온도·위치·이상탐지·알림·예측이 실시간으로 그대로 넘어가던 갭이었다(PR2에서 인지,
 * 여기서 해소). heartbeat만 예외로 전원에게 보낸다(화주 구분이 의미 없는 순수 연결 확인).
 */
@Service
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final TrackerOwnerCache trackerOwnerCache;
    private final Map<Long, List<SseEmitter>> emittersByShipper = new ConcurrentHashMap<>();

    /**
     * reading 이벤트 conflation 버퍼(M6) — 트래커당 최신값만 남기고 1초 주기로 flush한다.
     * baseline 부하테스트(5000 트래커=수집 1000/s)에서 이벤트당 즉시 send가 커넥션당
     * ~150건/s에서 캡을 치며 백로그가 무한 누적, e2e 반영이 290초까지 밀렸다. 온도·위치는
     * "최신값이 이전값을 대체"하는 데이터라 중간값을 버려도 정보 손실이 없고, 버퍼 크기가
     * 트래커 수로 유계라 백로그가 원리적으로 못 자란다. breach/anomaly 같은 상태 전이
     * 이벤트는 대체 불가(전이 자체가 정보)이므로 conflation 없이 즉시 보낸다.
     * 참고: 구독 커넥션 하나가 수천 트래커를 전부 받는 형상에선 flush 한 바퀴가 send 비용
     * × 트래커 수만큼 걸린다 — 커넥션당 송신량의 물리 한계는 남는다(부하테스트 리포트에 기록).
     */
    private final Map<String, ReadingStreamEvent> pendingReadings = new ConcurrentHashMap<>();

    public SseBroadcaster(TrackerOwnerCache trackerOwnerCache) {
        this.trackerOwnerCache = trackerOwnerCache;
    }

    public SseEmitter subscribe(Long shipperId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> shipperEmitters = emittersByShipper.computeIfAbsent(
                shipperId, id -> new CopyOnWriteArrayList<>());
        shipperEmitters.add(emitter);
        emitter.onCompletion(() -> shipperEmitters.remove(emitter));
        emitter.onTimeout(() -> shipperEmitters.remove(emitter));
        emitter.onError(e -> shipperEmitters.remove(emitter));
        return emitter;
    }

    /** 패키지 내부 테스트 전용 — conflation 버퍼 상태 검증용(send 자체는 컨테이너 없이 테스트 불가). */
    ReadingStreamEvent pendingReadingFor(String trackerId) {
        return pendingReadings.get(trackerId);
    }

    /** 패키지 내부 테스트 전용 접근자 — 초기화 안 된 SseEmitter.send()는 컨테이너 없이 항상
     * 예외를 던지므로(단위 테스트 불가), 실제 전송 대신 라우팅 결과(trackerId→어느 화주의
     * emitter 목록)만 검증한다. */
    List<SseEmitter> emittersFor(String trackerId) {
        Long shipperId = trackerOwnerCache.shipperIdOf(trackerId);
        if (shipperId == null) {
            return List.of();
        }
        return emittersByShipper.getOrDefault(shipperId, List.of());
    }

    /**
     * 수집(ingest) 요청 스레드가 SSE 전송을 기다리지 않도록 별도 스레드에서 처리한다(NFR-3와 동일한
     * 이유 — 예측/알림/실시간 스트림처럼 부가 기능은 절대 수집·저장 경로를 막으면 안 된다).
     * reading은 여기서 보내지 않고 conflation 버퍼에 적재만 한다 — 송신은 {@link #flushReadings}.
     */
    @Async
    @EventListener
    public void onReadingRecorded(ReadingRecordedEvent event) {
        TrackerStatus status = event.temperature().compareTo(event.thresholdTemp()) > 0
                ? TrackerStatus.BREACH
                : TrackerStatus.SAFE;

        pendingReadings.put(event.trackerId(), new ReadingStreamEvent(
                event.trackerId(),
                event.temperature(),
                GeoPoints.lat(event.position()),
                GeoPoints.lon(event.position()),
                event.recordedAt(),
                status));

        if (event.justBreached()) {
            broadcastToTracker(event.trackerId(), "breach", new BreachStreamEvent(
                    event.trackerId(), event.temperature(), event.thresholdTemp(), event.recordedAt()));
        }
    }

    /**
     * conflation 버퍼 flush — 1초 주기(fixedDelay라 한 바퀴가 오래 걸리면 겹치지 않고 순연).
     * out-of-order 걱정 없음: 버퍼에는 어차피 tracker_latest 갱신(UPDATED)을 통과한 최신값만
     * 들어온다. 구독자가 없으면 send가 no-op이라 버퍼만 비운다.
     */
    @Scheduled(fixedDelay = 1_000)
    public void flushReadings() {
        for (String trackerId : pendingReadings.keySet()) {
            ReadingStreamEvent event = pendingReadings.remove(trackerId);
            if (event != null) {
                broadcastToTracker(trackerId, "reading", event);
            }
        }
    }

    // 탐지 트랜잭션 커밋 후에만 브로드캐스트 — 커밋 실패 시 DB에 없는 anomaly가 화면에 뜨는 것을 방지.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnomalyDetected(AnomalyDetectedEvent event) {
        broadcastToTracker(event.trackerId(), "anomaly", new AnomalyStreamEvent(
                event.trackerId(), event.type(), event.severity(), event.message(), event.ts(), event.status()));
    }

    @Async
    @EventListener
    public void onAlertRaised(AlertRaisedEvent event) {
        broadcastToTracker(event.trackerId(), "alert", new AlertStreamEvent(
                event.id(), event.trackerId(), event.type(), event.severity(), event.status(), event.createdAt()));
    }

    // 예측 트랜잭션 커밋 후에만 브로드캐스트 — anomaly와 동일한 이유.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPredictionChanged(PredictionChangedEvent event) {
        broadcastToTracker(event.trackerId(), "prediction", new PredictionStreamEvent(
                event.trackerId(), event.status(), event.predictedBreachAt(), event.slopePerMinute(),
                event.modelVersion(), event.createdAt()));
    }

    /** 화주 구분이 의미 없는 순수 연결 확인이라 예외적으로 전원에게 보낸다. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        Map<String, Instant> data = Map.of("serverTs", Instant.now());
        for (List<SseEmitter> shipperEmitters : emittersByShipper.values()) {
            send(shipperEmitters, "heartbeat", data);
        }
    }

    private void broadcastToTracker(String trackerId, String eventName, Object data) {
        send(emittersFor(trackerId), eventName, data);
    }

    private void send(List<SseEmitter> targets, String eventName, Object data) {
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("SSE 전송 실패, 구독 해제: {}", e.toString());
                targets.remove(emitter);
            }
        }
    }
}
