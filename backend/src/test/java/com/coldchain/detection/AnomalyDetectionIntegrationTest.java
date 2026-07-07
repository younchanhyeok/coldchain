package com.coldchain.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.detection.domain.AnomalyEvent;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import com.coldchain.detection.repository.AnomalyEventRepository;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @Transactional을 의도적으로 붙이지 않는다 — 이유는 IngestControllerTest 상단 주석 참고
 * (tracker_latest upsert의 REQUIRES_NEW가 테스트 트랜잭션에 감싸인 미커밋 tracker를 못 봄).
 * L2 분석은 @Async 리스너라 실행이 요청 스레드보다 늦다 — Awaitility로 결과를 기다린다.
 *
 * 리딩 간격은 10초로 시뮬레이션한다(recordedAt만 앞서고 실제 벽시계 시간은 거의 흐르지 않음) —
 * IngestController가 recordedAt이 서버 현재 시각보다 5분 넘게 미래면 거부하므로, 전체 시퀀스가
 * 그 한도 안에 들어오게 충분히 작은 간격을 쓴다. 조회 상한도 Instant.now()가 아니라
 * base 기준 고정 미래 시각을 써야 한다 — 실제로 몇 초 안 걸리는 테스트 실행 시간이 시뮬레이션한
 * (초 단위) 리딩 간격을 못 따라잡기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnomalyDetectionIntegrationTest {

    private static final Duration QUERY_WINDOW = Duration.ofHours(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnomalyEventRepository anomalyEventRepository;

    private String registerTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":30.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class).deviceKey();
    }

    /**
     * 이 시나리오는 리딩을 순서대로 하나씩 처리했을 때의 상태 전이(cleanStreak 누적 등)를 검증한다.
     * @Async 리스너는 호출 순서를 보장하지 않으므로(SimpleAsyncTaskExecutor) 각 리딩을 보낸 뒤
     * 짧게 대기해 다음 리딩을 보내기 전에 비동기 처리가 끝나게 한다 — 실제 IoT 트래커가 보고
     * 주기마다 하나씩 보내는 것과 같은 순서를 보장하기 위함.
     */
    private void sendReading(String trackerId, String deviceKey, double temperature, Instant recordedAt)
            throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", trackerId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.42, "lon": 127.12, "recordedAt": "%s"}
                                """.formatted(temperature, recordedAt)))
                .andExpect(status().isAccepted());
        Thread.sleep(150);
    }

    private List<AnomalyEvent> findAll(String trackerId, Instant base) {
        return anomalyEventRepository.findByTrackerIdAndTsBetweenOrderByTsDesc(
                trackerId, base.minusSeconds(1), base.plus(QUERY_WINDOW));
    }

    private List<AnomalyEvent> findByType(String trackerId, AnomalyType type, Instant base) {
        return anomalyEventRepository.findByTrackerIdAndTypeAndTsBetweenOrderByTsDesc(
                trackerId, type, base.minusSeconds(1), base.plus(QUERY_WINDOW));
    }

    @Test
    void fewerThanFiveReadingsNeverTriggersAnomalyRegardlessOfSpike() throws Exception {
        String trackerId = "TRK-ANOMALY-COLDSTART";
        String deviceKey = registerTracker(trackerId);
        Instant base = Instant.now();

        sendReading(trackerId, deviceKey, 5.0, base);
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(10));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(20));
        sendReading(trackerId, deviceKey, 50.0, base.plusSeconds(30)); // 4번째 — 콜드스타트 가드로 판정 자체 스킵

        // 비동기 리스너가 돌 시간을 준 뒤에도 anomaly_event가 전혀 없어야 한다(음성 케이스라 고정 대기)
        Thread.sleep(500);
        assertThat(findAll(trackerId, base)).isEmpty();
    }

    @Test
    void suddenAnomalyActivatesSuppressesWhileActiveThenClears() throws Exception {
        String trackerId = "TRK-ANOMALY-SUDDEN";
        String deviceKey = registerTracker(trackerId);
        Instant base = Instant.now();

        sendReading(trackerId, deviceKey, 5.0, base);
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(10));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(20));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(30));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(40)); // 윈도우 5개 충족, 아직 평탄

        sendReading(trackerId, deviceKey, 9.0, base.plusSeconds(50)); // +24℃/분 — SUDDEN 활성화

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AnomalyEvent> events = findAll(trackerId, base);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getStatus()).isEqualTo(AnomalyStatus.ACTIVE);
        });

        sendReading(trackerId, deviceKey, 13.0, base.plusSeconds(60)); // 여전히 +24℃/분 — 억제(새 행 없음)

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AnomalyEvent> events = findAll(trackerId, base);
            assertThat(events).hasSize(1); // 여전히 1건 — 억제 확인
            assertThat(events.get(0).getStatus()).isEqualTo(AnomalyStatus.ACTIVE);
        });

        sendReading(trackerId, deviceKey, 13.0, base.plusSeconds(70)); // cleanStreak 1
        sendReading(trackerId, deviceKey, 13.0, base.plusSeconds(80)); // cleanStreak 2
        sendReading(trackerId, deviceKey, 13.0, base.plusSeconds(90)); // cleanStreak 3 → CLEARED

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AnomalyEvent> events = findAll(trackerId, base);
            assertThat(events).hasSize(1); // 새 행이 생기지 않고 같은 행이 닫혔는지 확인
            assertThat(events.get(0).getStatus()).isEqualTo(AnomalyStatus.CLEARED);
            assertThat(events.get(0).getClearedAt()).isNotNull();
        });
    }

    @Test
    void gradualAnomalyActivatesOnSustainedSlope() throws Exception {
        String trackerId = "TRK-ANOMALY-GRADUAL";
        String deviceKey = registerTracker(trackerId);
        Instant base = Instant.now();

        sendReading(trackerId, deviceKey, 5.00, base);
        sendReading(trackerId, deviceKey, 5.06, base.plusSeconds(10));
        sendReading(trackerId, deviceKey, 5.12, base.plusSeconds(20));
        sendReading(trackerId, deviceKey, 5.18, base.plusSeconds(30));
        sendReading(trackerId, deviceKey, 5.24, base.plusSeconds(40));
        sendReading(trackerId, deviceKey, 5.30, base.plusSeconds(50)); // 10초당 +0.06℃ = 분당 +0.36℃ 지속 상승

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<AnomalyEvent> events = findByType(trackerId, AnomalyType.GRADUAL, base);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getStatus()).isEqualTo(AnomalyStatus.ACTIVE);
        });
    }
}
