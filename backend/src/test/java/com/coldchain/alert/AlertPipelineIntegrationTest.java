package com.coldchain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.domain.AlertStatus;
import com.coldchain.alert.domain.AlertType;
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

/**
 * @Transactional을 의도적으로 붙이지 않는다 — 이유는 IngestControllerTest 상단 주석 참고.
 * SlackAlertSender는 실제 HTTP 호출 없이 Mockito로 대체 — 재시도 자체는 SlackAlertSenderTest가
 * 순수 단위 테스트로 검증하고, 여기서는 AlertService가 성공/실패 결과를 올바르게 처리하는지만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class AlertPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @MockitoBean
    private SlackAlertSender slackAlertSender;

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

    private List<Alert> findByTracker(String trackerId) {
        return alertRepository.findAll().stream()
                .filter(a -> a.getTrackerId().equals(trackerId))
                .toList();
    }

    @Test
    void breachAlertSavesPendingBeforeAttemptingSend() throws Exception {
        String trackerId = "TRK-ALERT-PENDING";
        String deviceKey = registerTracker(trackerId, 8.0);

        when(slackAlertSender.send(anyString())).thenAnswer(invocation -> {
            // Slack 발송을 시도하는 이 시점에 이미 PENDING으로 저장돼 있어야 한다
            List<Alert> pending = findByTracker(trackerId);
            assertThat(pending).as("alerts=%s", pending).hasSize(1);
            assertThat(pending.get(0).getStatus()).isEqualTo(AlertStatus.PENDING);
            return SlackAlertSender.SendResult.success(1);
        });

        sendReading(trackerId, deviceKey, 5.0, Instant.now());
        sendReading(trackerId, deviceKey, 9.0, Instant.now()); // SAFE→BREACH 전이

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getStatus()).isEqualTo(AlertStatus.SENT);
            assertThat(alerts.get(0).getType()).isEqualTo(AlertType.BREACH);
            assertThat(alerts.get(0).getMessage()).contains(trackerId).contains("긴급");
        });
    }

    @Test
    void breachAlertMarkedFailedWhenSendKeepsFailing() throws Exception {
        String trackerId = "TRK-ALERT-FAILED";
        String deviceKey = registerTracker(trackerId, 8.0);

        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.failed(3));

        sendReading(trackerId, deviceKey, 5.0, Instant.now());
        sendReading(trackerId, deviceKey, 9.0, Instant.now());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getStatus()).isEqualTo(AlertStatus.FAILED);
            assertThat(alerts.get(0).getRetryCount()).isEqualTo(2);
        });
    }

    @Test
    void failedSendReleasesDedupLockSoNextTransitionCanRetryImmediately() throws Exception {
        String trackerId = "TRK-ALERT-RETRY-AFTER-FAIL";
        String deviceKey = registerTracker(trackerId, 8.0);
        // 1차 시도는 실패, 2차 시도는 성공 — dedup 락이 실패 시 풀리지 않으면 2차가 억제돼 1건만 남는다.
        when(slackAlertSender.send(anyString()))
                .thenReturn(SlackAlertSender.SendResult.failed(3))
                .thenReturn(SlackAlertSender.SendResult.success(1));

        sendReading(trackerId, deviceKey, 5.0, Instant.now());
        sendReading(trackerId, deviceKey, 9.0, Instant.now()); // 1차 BREACH 전이 — 발송 실패

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).as("alerts=%s", alerts).hasSize(1);
            assertThat(alerts.get(0).getStatus()).isEqualTo(AlertStatus.FAILED);
        });

        sendReading(trackerId, deviceKey, 5.0, Instant.now()); // SAFE 복귀
        sendReading(trackerId, deviceKey, 9.0, Instant.now()); // 2차 BREACH 전이 — 억제되면 안 됨(락 해제됨)

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).as("alerts=%s", alerts).hasSize(2);
            assertThat(alerts).anyMatch(a -> a.getStatus() == AlertStatus.SENT);
        });
    }

    @Test
    void duplicateBreachWithinDedupWindowIsSuppressed() throws Exception {
        String trackerId = "TRK-ALERT-DEDUP";
        String deviceKey = registerTracker(trackerId, 8.0);
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        sendReading(trackerId, deviceKey, 5.0, Instant.now());
        sendReading(trackerId, deviceKey, 9.0, Instant.now()); // 1차 BREACH 전이 — 발송
        sendReading(trackerId, deviceKey, 5.0, Instant.now()); // SAFE로 복귀
        sendReading(trackerId, deviceKey, 9.0, Instant.now()); // 2차 BREACH 전이 — dedup 윈도우 내, 억제돼야 함

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).as("alerts=%s", alerts).hasSize(1);
        });
    }

    @Test
    void anomalyAndBreachDedupAreIndependent() throws Exception {
        String trackerId = "TRK-ALERT-CROSS";
        // 임계를 높게 잡아 SUDDEN 감지(9.0)로는 BREACH가 안 되고, 이후 별도로 진짜 BREACH를 낸다
        String deviceKey = registerTracker(trackerId, 20.0);
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        Instant base = Instant.now();
        sendReading(trackerId, deviceKey, 5.0, base);
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(10));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(20));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(30));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(40));
        sendReading(trackerId, deviceKey, 9.0, base.plusSeconds(50)); // SUDDEN 활성화 → ANOMALY 알림

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).as("alerts=%s", alerts)
                    .anyMatch(a -> a.getType() == AlertType.ANOMALY);
        });

        sendReading(trackerId, deviceKey, 25.0, base.plusSeconds(60)); // 진짜 BREACH — 억제되면 안 됨

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Alert> alerts = findByTracker(trackerId);
            assertThat(alerts).as("alerts=%s", alerts)
                    .anyMatch(a -> a.getType() == AlertType.BREACH);
        });
    }
}
