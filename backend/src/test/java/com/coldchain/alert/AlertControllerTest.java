package com.coldchain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.repository.AlertRepository;
import com.coldchain.alert.service.SlackAlertSender;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @MockitoBean
    private SlackAlertSender slackAlertSender;

    private String registerTracker(String trackerId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/trackers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackerId":"%s","productName":"백신 A","thresholdTemp":8.0}
                                """.formatted(trackerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TrackerRegisterResponse.class).deviceKey();
    }

    private void sendReading(String trackerId, String deviceKey, double temperature) throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", trackerId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.42, "lon": 127.12, "recordedAt": "%s"}
                                """.formatted(temperature, Instant.now()))
                        )
                .andExpect(status().isAccepted());
        Thread.sleep(150);
    }

    @Test
    void listFiltersByTrackerIdAndPaginates() throws Exception {
        String trackerId = "TRK-ALERT-LIST";
        String deviceKey = registerTracker(trackerId);
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        sendReading(trackerId, deviceKey, 5.0);
        sendReading(trackerId, deviceKey, 9.0); // BREACH 전이 → alert 1건 발송

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = alertRepository.findAll().stream()
                    .filter(a -> a.getTrackerId().equals(trackerId)).toList();
            assertThat(alerts).hasSize(1);
        });

        mockMvc.perform(get("/api/v1/alerts").param("trackerId", trackerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].trackerId").value(trackerId))
                .andExpect(jsonPath("$.content[0].type").value("BREACH"))
                .andExpect(jsonPath("$.content[0].message").isNotEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listReturnsEmptyForUnknownTracker() throws Exception {
        mockMvc.perform(get("/api/v1/alerts").param("trackerId", "TRK-NO-ALERTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
