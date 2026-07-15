package com.coldchain.ingest.producer;

import com.coldchain.common.error.IngestUnavailableException;
import com.coldchain.ingest.config.KafkaIngestConfig;
import com.coldchain.ingest.dto.ReadingMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * `readings` 토픽 프로듀서 — key=trackerId(트래커별 순서 보장), linger 배칭은 팩토리 설정.
 * send를 **블로킹으로 확인**한다: 202가 "브로커가 영속했다"를 의미해야 하므로 fire-and-forget이면
 * 202가 거짓말이 된다. linger(5ms)+확인 대기가 p99에 얹히는 비용은 부하테스트로 측정해 기록.
 * 브로커 장애 시 503 — 브로커는 이제 수집 경로의 일부다(PG가 죽으면 수집이 죽는 것과 동일).
 */
@Component
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "kafka", matchIfMissing = true)
public class ReadingProducer {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, ReadingMessage> kafkaTemplate;

    public ReadingProducer(KafkaTemplate<String, ReadingMessage> readingKafkaTemplate) {
        this.kafkaTemplate = readingKafkaTemplate;
    }

    public void send(ReadingMessage message) {
        await(kafkaTemplate.send(KafkaIngestConfig.READINGS_TOPIC, message.trackerId(), message));
    }

    public void sendAll(List<ReadingMessage> messages) {
        // 전부 발행해두고 한 번에 확인 — linger 배칭과 맞물려 배치가 왕복 1~2회로 나간다.
        List<CompletableFuture<SendResult<String, ReadingMessage>>> futures = messages.stream()
                .map(m -> kafkaTemplate.send(KafkaIngestConfig.READINGS_TOPIC, m.trackerId(), m))
                .toList();
        futures.forEach(this::await);
    }

    private void await(CompletableFuture<SendResult<String, ReadingMessage>> future) {
        try {
            future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestUnavailableException("수집 큐 발행이 중단되었습니다.");
        } catch (Exception e) {
            throw new IngestUnavailableException("수집 큐 발행에 실패했습니다: " + e.getMessage());
        }
    }
}
