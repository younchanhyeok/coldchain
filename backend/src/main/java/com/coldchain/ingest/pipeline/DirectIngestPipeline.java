package com.coldchain.ingest.pipeline;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.OutOfOrderConflictException;
import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.ingest.service.ReadingRecordedPublisher;
import com.coldchain.reading.dto.NewReading;
import com.coldchain.reading.service.ReadingService;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.service.TrackerLatestService;
import com.coldchain.tracker.service.TrackerLatestUpsertOutcome;
import com.coldchain.tracker.service.TrackerLatestUpsertResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * M1~M5의 동기 저장 경로 — 요청 스레드에서 원시 저장 + 최신상태 upsert까지 끝내고 202.
 * M6 Kafka 전환 후에도 A/B 측정·비교 기준으로 유지한다(app.ingest.mode=direct).
 */
@Component
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "direct")
public class DirectIngestPipeline implements IngestPipeline {

    private final ReadingService readingService;
    private final TrackerLatestService trackerLatestService;
    private final ReadingRecordedPublisher readingRecordedPublisher;

    public DirectIngestPipeline(ReadingService readingService, TrackerLatestService trackerLatestService,
            ReadingRecordedPublisher readingRecordedPublisher) {
        this.readingService = readingService;
        this.trackerLatestService = trackerLatestService;
        this.readingRecordedPublisher = readingRecordedPublisher;
    }

    @Override
    public void ingest(Tracker tracker, ReadingIngestRequest request) {
        BigDecimal temperature = BigDecimal.valueOf(request.temperature());
        Point position = GeoPoints.of(request.lat(), request.lon());

        readingService.save(tracker.getId(), request.recordedAt(), temperature, position);
        upsertLatestAndPublish(tracker, request.recordedAt(), temperature, position);
    }

    @Override
    public void ingestBatch(Tracker tracker, List<ReadingIngestRequest> readings) {
        readingService.saveBatch(readings.stream()
                .map(r -> new NewReading(tracker.getId(), r.recordedAt(), BigDecimal.valueOf(r.temperature()),
                        r.lat(), r.lon()))
                .toList());

        // 최신상태 upsert는 배열 중 최신 recordedAt 1건으로 collapse — Kafka 컨슈머와 같은 규칙.
        readings.stream().max(Comparator.comparing(ReadingIngestRequest::recordedAt)).ifPresent(newest ->
                upsertLatestAndPublish(tracker, newest.recordedAt(),
                        BigDecimal.valueOf(newest.temperature()), GeoPoints.of(newest.lat(), newest.lon())));
    }

    private void upsertLatestAndPublish(Tracker tracker, Instant recordedAt, BigDecimal temperature, Point position) {
        TrackerLatestUpsertResult result = trackerLatestService.upsert(
                tracker.getId(), recordedAt, temperature, position);

        // 409는 direct 모드 한정 — kafka 모드에선 파티션이 트래커별 쓰기를 직렬화해 충돌 자체가 없다.
        if (result.outcome() == TrackerLatestUpsertOutcome.CONFLICT) {
            throw new OutOfOrderConflictException(
                    "최신 상태 갱신 충돌로 재시도를 모두 소진했습니다: " + tracker.getId());
        }

        if (result.outcome() == TrackerLatestUpsertOutcome.UPDATED) {
            readingRecordedPublisher.publish(tracker.getId(), temperature, position, recordedAt,
                    tracker.getThresholdTemp(), result.previousTemperature());
        }
    }
}
