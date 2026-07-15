package com.coldchain.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.KafkaTestcontainersConfiguration;
import com.coldchain.ingest.config.KafkaIngestConfig;
import com.coldchain.reading.repository.ReadingRepository;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * kafka 모드(M6~ 기본) 수집 경로 e2e — 202는 "브로커 영속"만 보장하고 저장·최신상태는
 * 컨슈머가 비동기로 처리하므로 전부 Awaitility로 결과를 기다린다(direct 모드의 "202 직후
 * 조회 가능" 계약을 검증하는 IngestControllerTest와 대비되는 지점).
 * @Transactional 미사용 이유는 IngestControllerTest 상단 주석 참고.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ingest.mode=kafka")
@Import({KafkaTestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class KafkaIngestPipelineIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private TrackerLatestRepository trackerLatestRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private TrackerRegisterResponse registerTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class);
    }

    private void postReading(TrackerRegisterResponse tracker, double temperature, Instant recordedAt)
            throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.49, "lon": 127.02, "recordedAt": "%s"}
                                """.formatted(temperature, recordedAt)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    private long readingCount(String trackerId) {
        return readingRepository.findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                trackerId, Instant.EPOCH, Instant.now().plus(Duration.ofHours(1)),
                PageRequest.of(0, 100)).size();
    }

    @Test
    void singleReadingFlowsThroughBrokerToStorageAndLatest() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-KAFKA-001");
        Instant recordedAt = Instant.now();

        postReading(tracker, 5.8, recordedAt);

        await().atMost(WAIT).ignoreExceptions().untilAsserted(() -> {
            assertThat(readingCount(tracker.trackerId())).isEqualTo(1);
            TrackerLatest latest = trackerLatestRepository.findById(tracker.trackerId()).orElseThrow();
            assertThat(latest.getLastTemp()).isEqualByComparingTo("5.8");
        });
    }

    @Test
    void duplicateDeliveryIsIdempotent() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-KAFKA-002");
        Instant recordedAt = Instant.now();

        postReading(tracker, 5.8, recordedAt);
        postReading(tracker, 5.8, recordedAt); // 같은 (tracker, recordedAt) 재전송 — at-least-once 재현

        // 중복 스킵의 비동기 검증: 나중에 보낸 세 번째(구별되는) 리딩이 반영될 때까지 기다린 뒤
        // 총 개수가 2인지 확인한다 — "아직 처리 전이라 1"과 "중복이 걸러져 2"를 구별하기 위함.
        postReading(tracker, 6.1, recordedAt.plusSeconds(5));

        await().atMost(WAIT).ignoreExceptions().untilAsserted(() -> {
            TrackerLatest latest = trackerLatestRepository.findById(tracker.trackerId()).orElseThrow();
            assertThat(latest.getLastTemp()).isEqualByComparingTo("6.1");
        });
        assertThat(readingCount(tracker.trackerId())).isEqualTo(2);
    }

    @Test
    void batchCollapsesLatestToNewestRecordedAt() throws Exception {
        TrackerRegisterResponse tracker = registerTracker("TRK-KAFKA-003");
        Instant base = Instant.now().minusSeconds(60);

        mockMvc.perform(post("/api/v1/trackers/{id}/readings", tracker.trackerId())
                        .header("X-Device-Key", tracker.deviceKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"temperature": 5.1, "lat": 37.49, "lon": 127.02, "recordedAt": "%s"},
                                  {"temperature": 5.9, "lat": 37.50, "lon": 127.03, "recordedAt": "%s"},
                                  {"temperature": 5.5, "lat": 37.49, "lon": 127.02, "recordedAt": "%s"}
                                ]
                                """.formatted(base, base.plusSeconds(20), base.plusSeconds(10))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(3));

        await().atMost(WAIT).ignoreExceptions().untilAsserted(() -> {
            assertThat(readingCount(tracker.trackerId())).isEqualTo(3);
            TrackerLatest latest = trackerLatestRepository.findById(tracker.trackerId()).orElseThrow();
            assertThat(latest.getLastTemp()).isEqualByComparingTo("5.9"); // 배열 중 최신 recordedAt
        });
    }

    @Test
    void poisonMessageDoesNotBlockPartition() throws Exception {
        // 우리 프로듀서는 항상 유효한 JSON을 만들므로, 브로커에 직접 깨진 페이로드를 넣어
        // 역직렬화 실패가 파티션을 막지 않는 것을 검증한다 — 같은 key(=같은 파티션)로 poison을
        // 먼저 넣고, 그 뒤에 발행되는 정상 리딩이 처리되면 통과. (배치 리스너 + ErrorHandling
        // Deserializer에서 역직렬화 실패는 null로 들어와 컨슈머가 스킵한다 — 문서화된 패턴.
        // DLT는 처리 실패(재시도 소진) 전용.)
        String trackerId = "TRK-KAFKA-004";
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(KafkaIngestConfig.READINGS_TOPIC, trackerId, "not-json{"));
            producer.flush();
        }

        TrackerRegisterResponse tracker = registerTracker(trackerId);
        postReading(tracker, 4.2, Instant.now());

        await().atMost(WAIT).ignoreExceptions().untilAsserted(() ->
                assertThat(readingCount(trackerId)).isEqualTo(1));
    }
}
