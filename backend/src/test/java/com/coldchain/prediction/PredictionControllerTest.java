package com.coldchain.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.prediction.service.PredictionClient;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PredictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PredictionRepository predictionRepository;

    @MockitoBean
    private PredictionClient predictionClient;

    private String registerTracker(String trackerId, double thresholdTemp) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":%s}
                                """.formatted(trackerId, thresholdTemp)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class).deviceKey();
    }

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
    void returnsNoneWhenTrackerHasNoPrediction() throws Exception {
        String trackerId = "TRK-PREDICTION-NONE";
        registerTracker(trackerId, 8.0);

        mockMvc.perform(get("/api/v1/trackers/{id}/prediction", trackerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void returnsActivePredictionWithTwoPointForecast() throws Exception {
        String trackerId = "TRK-PREDICTION-ACTIVE";
        String deviceKey = registerTracker(trackerId, 20.0);

        Instant breachAt = Instant.now().plusSeconds(600);
        when(predictionClient.predict(anyString(), any(), any()))
                .thenReturn(Optional.of(new PredictionClient.Result(true, breachAt, BigDecimal.valueOf(0.5), "v1-linear")));

        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(i * 10L));
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE))
                        .isPresent());

        mockMvc.perform(get("/api/v1/trackers/{id}/prediction", trackerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.modelVersion").value("v1-linear"))
                .andExpect(jsonPath("$.leadTimeMinutes").isNumber())
                .andExpect(jsonPath("$.forecast.length()").value(2));
    }
}
