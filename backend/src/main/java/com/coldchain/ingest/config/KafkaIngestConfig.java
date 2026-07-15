package com.coldchain.ingest.config;

import com.coldchain.ingest.dto.ReadingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 수집 토픽 구성(M6 PR3). 전부 app.ingest.mode=kafka(기본)에서만 활성 — direct 모드
 * (A/B 비교·기존 통합 테스트)에서는 브로커 연결 시도 자체가 없어야 한다.
 *
 * - 파티션 6, key=trackerId: 트래커별 순서 보장 = 기존 in-JVM 트래커별 락의 분산 대체물.
 * - linger 5ms: 프로듀서 배칭. 202 응답에 최대 그만큼 얹힌다 — 부하테스트로 측정해 기록.
 * - at-least-once: 오프셋은 배치 리스너 정상 반환 후 커밋. 재전달은 reading 유니크 제약
 *   (V10) + latest recordedAt guard가 흡수한다. Redis 윈도우 push는 비멱등 — 재전달 시
 *   z-score가 일시 왜곡될 수 있음을 한계로 문서화(리포트).
 * - 처리 실패는 1s×2 재시도 후 readings.DLT로 — poison 메시지가 파티션을 막지 않게.
 */
@Configuration
@ConditionalOnProperty(name = "app.ingest.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaIngestConfig {

    public static final String READINGS_TOPIC = "readings";
    public static final String READINGS_DLT = "readings.DLT";

    @Bean
    public NewTopic readingsTopic() {
        return TopicBuilder.name(READINGS_TOPIC).partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic readingsDltTopic() {
        return TopicBuilder.name(READINGS_DLT).partitions(6).replicas(1).build();
    }

    @Bean
    public ProducerFactory<String, ReadingMessage> readingProducerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // acks=all + 재전송 중복 방지
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        // Spring의 ObjectMapper 재사용 — JSR310(Instant) 직렬화 설정을 앱 전체와 통일.
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(),
                new JsonSerializer<>(objectMapper));
    }

    @Bean
    public KafkaTemplate<String, ReadingMessage> readingKafkaTemplate(
            ProducerFactory<String, ReadingMessage> readingProducerFactory) {
        return new KafkaTemplate<>(readingProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, ReadingMessage> readingConsumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500); // poll 배치 = insert 배치 상한
        JsonDeserializer<ReadingMessage> valueDeserializer =
                new JsonDeserializer<>(ReadingMessage.class, objectMapper);
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReadingMessage> readingListenerContainerFactory(
            ConsumerFactory<String, ReadingMessage> readingConsumerFactory,
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        ConcurrentKafkaListenerContainerFactory<String, ReadingMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(readingConsumerFactory);
        factory.setBatchListener(true);
        // 파티션 수(6)와 동일 — 5000 트래커 실측에서 concurrency 3은 소비 494/s로 유입(1000/s)을
        // 못 따라가 컨슈머 랙이 무한 성장했다(e2e p50 82s). 컨슈머 처리량의 지배 비용은 리딩별
        // L2 분석이므로 스레드당 1파티션이 단일 인스턴스의 상한 — 그 이상은 수평 확장(인스턴스
        // 추가 시 파티션 재분배)의 영역이다.
        factory.setConcurrency(6);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(dltTemplate(kafkaProperties, objectMapper),
                        (record, ex) -> new TopicPartition(READINGS_DLT, record.partition())),
                new FixedBackOff(1_000L, 2)));
        return factory;
    }

    /** DLT 전용 템플릿 — 역직렬화 실패 레코드의 key/value는 byte[]로 올 수 있어 타입별로 위임한다. */
    private KafkaTemplate<Object, Object> dltTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(config);
        factory.setKeySerializerSupplier(() -> new DelegatingByTypeSerializer(objectMapper));
        factory.setValueSerializerSupplier(() -> new DelegatingByTypeSerializer(objectMapper));
        return new KafkaTemplate<>(factory);
    }

    /** byte[]·String은 그대로, 나머지는 JSON — DeadLetterPublishingRecoverer 권장 패턴의 최소 구현. */
    private static final class DelegatingByTypeSerializer
            implements org.apache.kafka.common.serialization.Serializer<Object> {
        private final ByteArraySerializer bytes = new ByteArraySerializer();
        private final StringSerializer strings = new StringSerializer();
        private final JsonSerializer<Object> json;

        private DelegatingByTypeSerializer(ObjectMapper objectMapper) {
            this.json = new JsonSerializer<>(objectMapper);
        }

        @Override
        public byte[] serialize(String topic, Object data) {
            if (data instanceof byte[] raw) {
                return bytes.serialize(topic, raw);
            }
            if (data instanceof String text) {
                return strings.serialize(topic, text);
            }
            return json.serialize(topic, data);
        }
    }
}
