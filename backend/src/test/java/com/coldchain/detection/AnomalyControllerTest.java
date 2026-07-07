package com.coldchain.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.detection.domain.AnomalyEvent;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnomalyControllerTest {

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
     * @Async 리스너는 호출 순서를 보장하지 않으므로(SimpleAsyncTaskExecutor) 각 리딩 전송 후
     * 짧게 대기해 다음 리딩 전에 비동기 처리가 끝나게 한다.
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

    @Test
    void returnsDetectedAnomaliesWithinRange() throws Exception {
        String trackerId = "TRK-ANOMALY-QUERY";
        String deviceKey = registerTracker(trackerId);
        Instant base = Instant.now();

        sendReading(trackerId, deviceKey, 5.0, base);
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(10));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(20));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(30));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(40));
        sendReading(trackerId, deviceKey, 9.0, base.plusSeconds(50)); // SUDDEN 활성화

        // 먼저 리포지토리로 실제 감지가 끝났는지 확인한다(AnomalyDetectionIntegrationTest에서
        // 이미 검증된 신뢰 가능한 대기 방식) — HTTP GET을 반복 폴링하는 대신, 데이터가 갖춰진 뒤
        // 컨트롤러 계약(JSON 응답 shape)만 단발성으로 확인한다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<AnomalyEvent> events = anomalyEventRepository.findByTrackerIdAndTypeAndTsBetweenOrderByTsDesc(
                    trackerId, AnomalyType.SUDDEN, base.minusSeconds(1), base.plusSeconds(3600));
            assertThat(events).as("events=%s", events).hasSize(1);
        });

        mockMvc.perform(get("/api/v1/trackers/{id}/anomalies", trackerId)
                        .param("from", base.minusSeconds(1).toString())
                        .param("to", base.plusSeconds(3600).toString())
                        .param("type", "SUDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomalies.length()").value(1))
                .andExpect(jsonPath("$.anomalies[0].type").value("SUDDEN"))
                .andExpect(jsonPath("$.anomalies[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.anomalies[0].status").value("ACTIVE"));
    }

    @Test
    void returnsEmptyListWhenNoAnomalyDetected() throws Exception {
        String trackerId = "TRK-ANOMALY-QUERY-EMPTY";
        registerTracker(trackerId);

        mockMvc.perform(get("/api/v1/trackers/{id}/anomalies", trackerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomalies.length()").value(0));
    }
}
