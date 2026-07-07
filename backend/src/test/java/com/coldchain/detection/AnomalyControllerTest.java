package com.coldchain.detection;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
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

        // 조회 상한은 Instant.now()가 아니라 base 기준 고정 미래 시각을 쓴다 — 테스트 실행은
        // 순식간이라 실제 시계가 시뮬레이션한 리딩 간격(초 단위)을 못 따라잡기 때문이다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/trackers/{id}/anomalies", trackerId)
                                .param("from", base.minusSeconds(1).toString())
                                .param("to", base.plusSeconds(3600).toString()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.anomalies.length()").value(1))
                        .andExpect(jsonPath("$.anomalies[0].type").value("SUDDEN"))
                        .andExpect(jsonPath("$.anomalies[0].severity").value("HIGH"))
                        .andExpect(jsonPath("$.anomalies[0].status").value("ACTIVE")));
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
