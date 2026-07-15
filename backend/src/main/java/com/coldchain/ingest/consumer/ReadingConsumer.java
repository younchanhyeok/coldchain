package com.coldchain.ingest.consumer;

import com.coldchain.common.GeoPoints;
import com.coldchain.detection.service.AnomalyDetectionService;
import com.coldchain.ingest.config.KafkaIngestConfig;
import com.coldchain.ingest.dto.ReadingMessage;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.ingest.service.ReadingRecordedPublisher;
import com.coldchain.reading.dto.NewReading;
import com.coldchain.reading.service.ReadingService;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerLatestService;
import com.coldchain.tracker.service.TrackerLatestUpsertOutcome;
import com.coldchain.tracker.service.TrackerLatestUpsertResult;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * `readings` 배치 컨슈머 — M6에서 L1 저장·최신상태·L2 트리거가 요청 스레드에서 여기로 이동했다.
 *
 * poll 배치 처리 3단계:
 *   ① 원시 배치 insert — JDBC 문장 배칭 1왕복(직전엔 리딩당 1왕복). 멱등(ON CONFLICT).
 *   ② 트래커별 최신 recordedAt 1건으로 collapse해 latest upsert — 같은 트래커 리딩이 배치에
 *      몰릴수록 upsert 횟수가 급감한다. UPDATED면 ReadingRecordedEvent 발행(SSE·예측·알림).
 *   ③ L2 이상탐지 — 리딩별로 파티션 순서 그대로 컨슈머 스레드에서 동기 호출. 같은 트래커는
 *      같은 파티션 = 같은 스레드이므로 순서가 구조적으로 보장된다(직전엔 in-JVM 락으로 직렬화).
 *
 * 오프셋은 이 메서드가 정상 반환한 뒤 커밋(at-least-once). 재전달은 ①의 유니크 제약과
 * ②의 recordedAt guard가 흡수한다. 탐지(③)의 개별 실패는 잡아서 스킵 — 원시는 이미
 * 저장됐으므로 탐지 실패가 배치를 DLT로 보내면 오히려 손해다(NFR-3).
 */
@Component
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "kafka", matchIfMissing = true)
public class ReadingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReadingConsumer.class);

    private final TrackerRepository trackerRepository;
    private final ReadingService readingService;
    private final TrackerLatestService trackerLatestService;
    private final ReadingRecordedPublisher readingRecordedPublisher;
    private final AnomalyDetectionService anomalyDetectionService;

    public ReadingConsumer(TrackerRepository trackerRepository, ReadingService readingService,
            TrackerLatestService trackerLatestService, ReadingRecordedPublisher readingRecordedPublisher,
            AnomalyDetectionService anomalyDetectionService) {
        this.trackerRepository = trackerRepository;
        this.readingService = readingService;
        this.trackerLatestService = trackerLatestService;
        this.readingRecordedPublisher = readingRecordedPublisher;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @KafkaListener(topics = KafkaIngestConfig.READINGS_TOPIC, groupId = "coldchain-ingest",
            containerFactory = "readingListenerContainerFactory")
    public void onBatch(List<ReadingMessage> rawMessages) {
        // 역직렬화 실패는 ErrorHandlingDeserializer가 null 값으로 넘긴다 — 배치 리스너에서는
        // 직접 걸러야 한다(문서화된 패턴). 던지면 재시도가 유효 레코드까지 물고 늘어지므로
        // 스킵이 정답: 파티션을 막지 않는 것이 요점이고, 우리 프로듀서는 깨진 JSON을 만들지 않는다.
        List<ReadingMessage> messages = rawMessages.stream()
                .filter(m -> m != null && m.trackerId() != null && m.recordedAt() != null)
                .toList();
        if (messages.size() < rawMessages.size()) {
            log.warn("역직렬화 실패/필드 결손 {}건 스킵(파티션 비차단)", rawMessages.size() - messages.size());
        }
        if (messages.isEmpty()) {
            return;
        }

        // 임계값은 운영 중 바뀔 수 있는 트래커 속성 — 발행 시점이 아니라 소비 시점 DB 기준(배치 1쿼리).
        Map<String, Tracker> trackers = new HashMap<>();
        trackerRepository.findAllById(messages.stream().map(ReadingMessage::trackerId).distinct().toList())
                .forEach(t -> trackers.put(t.getId(), t));

        // 발행 후 삭제된 트래커의 메시지는 스킵 — FK 위반 한 건이 배치 전체를 DLT로 보내지 않게.
        List<ReadingMessage> known = messages.stream()
                .filter(m -> trackers.containsKey(m.trackerId()))
                .toList();
        if (known.size() < messages.size()) {
            log.warn("미등록 트래커 리딩 {}건 스킵", messages.size() - known.size());
        }

        // ① 원시 배치 insert
        readingService.saveBatch(known.stream()
                .map(m -> new NewReading(m.trackerId(), m.recordedAt(),
                        BigDecimal.valueOf(m.temperature()), m.lat(), m.lon()))
                .toList());

        // ② 트래커별 최신 1건 collapse → upsert → 발행
        Map<String, ReadingMessage> newestPerTracker = new HashMap<>();
        for (ReadingMessage message : known) {
            newestPerTracker.merge(message.trackerId(), message,
                    (a, b) -> a.recordedAt().isAfter(b.recordedAt()) ? a : b);
        }
        for (ReadingMessage newest : newestPerTracker.values()) {
            Tracker tracker = trackers.get(newest.trackerId());
            BigDecimal temperature = BigDecimal.valueOf(newest.temperature());
            Point position = GeoPoints.of(newest.lat(), newest.lon());

            TrackerLatestUpsertResult result = trackerLatestService.upsert(
                    newest.trackerId(), newest.recordedAt(), temperature, position);
            if (result.outcome() == TrackerLatestUpsertOutcome.CONFLICT) {
                // 파티션이 트래커별 쓰기를 직렬화하므로 정상 상황에선 나올 수 없다 —
                // 나온다면 설정 실수(같은 그룹 외 컨슈머, 수동 쓰기 등). 원시는 저장됐고
                // 다음 리딩이 latest를 복구하므로 배치를 실패시키지 않는다.
                log.warn("latest upsert 충돌(비정상 — 파티션 직렬화 하에선 불가): {}", newest.trackerId());
                continue;
            }
            if (result.outcome() == TrackerLatestUpsertOutcome.UPDATED) {
                readingRecordedPublisher.publish(newest.trackerId(), temperature, position,
                        newest.recordedAt(), tracker.getThresholdTemp(), result.previousTemperature());
            }
        }

        // ③ L2 이상탐지 — 리딩별, 수신(=파티션) 순서 그대로
        for (ReadingMessage message : known) {
            Tracker tracker = trackers.get(message.trackerId());
            try {
                anomalyDetectionService.analyze(new ReadingRecordedEvent(
                        message.trackerId(), BigDecimal.valueOf(message.temperature()),
                        GeoPoints.of(message.lat(), message.lon()), message.recordedAt(),
                        tracker.getThresholdTemp(), false)); // justBreached는 탐지가 안 쓴다 — 발행용 필드
            } catch (Exception e) {
                log.warn("L2 분석 실패, 스킵(원시는 저장됨): {} {}", message.trackerId(), e.toString());
            }
        }
    }
}
