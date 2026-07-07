package com.coldchain.stream;

import com.coldchain.common.GeoPoints;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.stream.dto.BreachStreamEvent;
import com.coldchain.stream.dto.ReadingStreamEvent;
import com.coldchain.tracker.domain.TrackerStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * 수집(ingest) 요청 스레드가 SSE 전송을 기다리지 않도록 별도 스레드에서 처리한다(NFR-3와 동일한
     * 이유 — 예측/알림/실시간 스트림처럼 부가 기능은 절대 수집·저장 경로를 막으면 안 된다).
     */
    @Async
    @EventListener
    public void onReadingRecorded(ReadingRecordedEvent event) {
        TrackerStatus status = event.temperature().compareTo(event.thresholdTemp()) > 0
                ? TrackerStatus.BREACH
                : TrackerStatus.SAFE;

        broadcast("reading", new ReadingStreamEvent(
                event.trackerId(),
                event.temperature(),
                GeoPoints.lat(event.position()),
                GeoPoints.lon(event.position()),
                event.recordedAt(),
                status));

        if (event.justBreached()) {
            broadcast("breach", new BreachStreamEvent(
                    event.trackerId(), event.temperature(), event.thresholdTemp(), event.recordedAt()));
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        broadcast("heartbeat", Map.of("serverTs", Instant.now()));
    }

    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("SSE 전송 실패, 구독 해제: {}", e.toString());
                emitters.remove(emitter);
            }
        }
    }
}
