package com.coldchain.ingest.pipeline;

import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.ingest.dto.ReadingMessage;
import com.coldchain.ingest.producer.ReadingProducer;
import com.coldchain.tracker.domain.Tracker;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * M6 기본 수집 경로 — 검증된 리딩을 `readings` 토픽으로 발행하고 202. 저장·최신상태·L2 트리거는
 * 전부 컨슈머(ReadingConsumer)의 배치 처리로 이동한다. 202의 의미가 "저장 완료"에서 "브로커
 * 영속 완료"로 바뀌지만 API 계약(202 Accepted, 다운스트림 비동기)은 M1 설계 그대로다.
 */
@Component
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaIngestPipeline implements IngestPipeline {

    private final ReadingProducer readingProducer;

    public KafkaIngestPipeline(ReadingProducer readingProducer) {
        this.readingProducer = readingProducer;
    }

    @Override
    public void ingest(Tracker tracker, ReadingIngestRequest request) {
        readingProducer.send(toMessage(tracker.getId(), request));
    }

    @Override
    public void ingestBatch(Tracker tracker, List<ReadingIngestRequest> readings) {
        // 같은 trackerId = 같은 파티션이므로 발행 순서가 소비 순서로 보존된다.
        readingProducer.sendAll(readings.stream()
                .map(r -> toMessage(tracker.getId(), r))
                .toList());
    }

    private static ReadingMessage toMessage(String trackerId, ReadingIngestRequest request) {
        return new ReadingMessage(trackerId, request.temperature(), request.lat(), request.lon(),
                request.recordedAt(), request.seq(), request.ambientTemp());
    }
}
