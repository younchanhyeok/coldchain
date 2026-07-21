package com.coldchain.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.DefaultShipperAuthConfig;
import com.coldchain.TestcontainersConfiguration;
import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.domain.AlertType;
import com.coldchain.alert.repository.AlertRepository;
import com.coldchain.alert.service.SlackAlertSender;
import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.repository.BreachEventRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.prediction.service.PredictionClient;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PredictionClient(Python 호출)는 Mockito로 대체한다 — CI에 uvicorn이 없으므로 실연동은
 * 수동 검증(bootRun+uvicorn+시뮬레이터)이 전담하고, 여기서는 PredictionService의 라이프사이클
 * 판정(생성/취소/무효화/적중/장애 시 무중단)만 검증한다. 설정용 리딩은 전부 평탄값(5.0)으로
 * 보내 L2 이상탐지가 우발적으로 SUDDEN을 트리거해 테스트를 흔드는 일이 없게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DefaultShipperAuthConfig.class})
class PredictionPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private BreachEventRepository breachEventRepository;

    @Autowired
    private AlertRepository alertRepository;

    @MockitoBean
    private PredictionClient predictionClient;

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

    private void sendFlatWarmup(String trackerId, String deviceKey, Instant base) throws Exception {
        for (int i = 0; i < 5; i++) {
            sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(i * 10L));
        }
    }

    private void sendReadingWithAmbient(String trackerId, String deviceKey, double temperature, double ambient,
            Instant recordedAt) throws Exception {
        mockMvc.perform(post("/api/v1/trackers/{id}/readings", trackerId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"temperature": %s, "lat": 37.42, "lon": 127.12, "recordedAt": "%s", "ambientTemp": %s}
                                """.formatted(temperature, recordedAt, ambient)))
                .andExpect(status().isAccepted());
        Thread.sleep(150);
    }

    @Test
    void contextAndWindowCarryAmbientFromReadings() throws Exception {
        // M7 PR1 배선 검증: 리딩에 실린 ambientTemp가 예측 호출의 context·window까지 흐른다.
        String trackerId = "TRK-PRED-AMBIENT";
        String deviceKey = registerTracker(trackerId, 20.0);
        when(predictionClient.predict(anyString(), any(), any(), any())).thenReturn(Optional.empty());

        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            sendReadingWithAmbient(trackerId, deviceKey, 5.0, 22.0, base.plusSeconds(i * 10L));
        }

        var windowCaptor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        var contextCaptor = org.mockito.ArgumentCaptor.forClass(PredictionClient.Context.class);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                org.mockito.Mockito.verify(predictionClient, org.mockito.Mockito.atLeastOnce())
                        .predict(anyString(), any(), windowCaptor.capture(), contextCaptor.capture()));

        assertThat(contextCaptor.getValue().ambientTemp()).isEqualByComparingTo("22.00");
        assertThat(windowCaptor.getValue()).allSatisfy(p ->
                assertThat(((PredictionClient.WindowPoint) p).ambientTemp()).isEqualByComparingTo("22.00"));
    }

    private Optional<Prediction> findActive(String trackerId) {
        return predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE);
    }

    private List<Alert> findAlertsByType(String trackerId, AlertType type) {
        return alertRepository.findAll().stream()
                .filter(a -> a.getTrackerId().equals(trackerId) && a.getType() == type)
                .toList();
    }

    @Test
    void createsActivePredictionAndSendsAlertWhenClientSaysWillBreach() throws Exception {
        String trackerId = "TRK-PRED-CREATE";
        String deviceKey = registerTracker(trackerId, 20.0); // 임계를 높게 잡아 실제 이탈은 안 나게

        Instant breachAt = Instant.now().plusSeconds(600);
        when(predictionClient.predict(anyString(), any(), any(), any()))
                .thenReturn(Optional.of(new PredictionClient.Result(true, breachAt, BigDecimal.valueOf(0.5), "v1-linear")));
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        sendFlatWarmup(trackerId, deviceKey, Instant.now());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<Prediction> active = findActive(trackerId);
            assertThat(active).isPresent();
            assertThat(active.get().getModelVersion()).isEqualTo("v1-linear");
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(findAlertsByType(trackerId, AlertType.PREDICTION)).hasSize(1));
    }

    @Test
    void cancelsOnlyAfterThreeConsecutiveCalmReadings() throws Exception {
        String trackerId = "TRK-PRED-CANCEL";
        String deviceKey = registerTracker(trackerId, 20.0);

        Instant breachAt = Instant.now().plusSeconds(600);
        // 처음엔 이탈 예상 → ACTIVE, 이후 계속 false → 연속 3회여야 취소(히스테리시스)
        when(predictionClient.predict(anyString(), any(), any(), any()))
                .thenReturn(Optional.of(new PredictionClient.Result(true, breachAt, BigDecimal.valueOf(0.5), "v1-linear")))
                .thenReturn(Optional.of(new PredictionClient.Result(false, null, BigDecimal.ZERO, "v1-linear")));
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        Instant base = Instant.now();
        sendFlatWarmup(trackerId, deviceKey, base);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(findActive(trackerId)).isPresent());

        // 추세 완화 리딩 2회 — 아직 취소되면 안 됨
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(60));
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(70));
        assertThat(findActive(trackerId)).as("2회만으론 취소되면 안 됨").isPresent();

        // 3회째 — 이제 취소돼야 함
        sendReading(trackerId, deviceKey, 5.0, base.plusSeconds(80));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(findActive(trackerId)).isEmpty());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(findAlertsByType(trackerId, AlertType.PREDICTION_CANCELED)).hasSize(1));
    }

    @Test
    void invalidatesActivePredictionOnSuddenAnomaly() throws Exception {
        String trackerId = "TRK-PRED-INVALIDATE";
        String deviceKey = registerTracker(trackerId, 20.0); // 급변 스파이크(15도)에도 실제 이탈은 안 나게

        Instant breachAt = Instant.now().plusSeconds(600);
        when(predictionClient.predict(anyString(), any(), any(), any()))
                .thenReturn(Optional.of(new PredictionClient.Result(true, breachAt, BigDecimal.valueOf(0.5), "v1-linear")));
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        Instant base = Instant.now();
        sendFlatWarmup(trackerId, deviceKey, base);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(findActive(trackerId)).isPresent());

        // 급변 — 순간 변화율(10도/10초=60도/분) > 1.5도/분 임계 → SUDDEN 활성화
        sendReading(trackerId, deviceKey, 15.0, base.plusSeconds(50));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<Prediction> latest = predictionRepository.findTopByTrackerIdOrderByCreatedAtDesc(trackerId);
            assertThat(latest).isPresent();
            assertThat(latest.get().getStatus()).isEqualTo(PredictionStatus.INVALIDATED);
        });
    }

    @Test
    void marksBreachedAndRecordsBreachEventOnActualBreach() throws Exception {
        String trackerId = "TRK-PRED-BREACH";
        String deviceKey = registerTracker(trackerId, 8.0);

        Instant breachAt = Instant.now().plusSeconds(600);
        when(predictionClient.predict(anyString(), any(), any(), any()))
                .thenReturn(Optional.of(new PredictionClient.Result(true, breachAt, BigDecimal.valueOf(0.5), "v1-linear")));
        when(slackAlertSender.send(anyString())).thenReturn(SlackAlertSender.SendResult.success(1));

        Instant base = Instant.now();
        sendFlatWarmup(trackerId, deviceKey, base);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(findActive(trackerId)).isPresent());

        // 완만한 이탈(4도 변화를 240초에 걸쳐 = 1.0도/분 < 1.5 SUDDEN 임계) — 이 테스트는
        // BREACHED 전이만 보려는 것이라 SUDDEN과의 경합(둘 다 @Async라 순서 보장 없음)을 피한다.
        sendReading(trackerId, deviceKey, 9.0, base.plusSeconds(280));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<Prediction> latest = predictionRepository.findTopByTrackerIdOrderByCreatedAtDesc(trackerId);
            assertThat(latest).isPresent();
            assertThat(latest.get().getStatus()).isEqualTo(PredictionStatus.BREACHED);
            assertThat(latest.get().getBreachedAt()).isNotNull();
        });

        assertThat(breachEventRepository.findAll().stream().anyMatch(b -> b.getTrackerId().equals(trackerId)))
                .isTrue();
    }

    @Test
    void skipsGracefullyWhenPredictionClientUnavailable() throws Exception {
        String trackerId = "TRK-PRED-UNAVAILABLE";
        String deviceKey = registerTracker(trackerId, 20.0);

        // 예측 서버 장애·쿨다운을 흉내— PredictionClient가 항상 빈 Optional을 반환.
        when(predictionClient.predict(anyString(), any(), any(), any())).thenReturn(Optional.empty());

        // sendReading 자체가 202를 기대하므로(NFR-3: 수집은 무중단), 이 호출들이 전부 통과하는
        // 것 자체가 이미 무중단 증거다 — 추가로 예측만 생성되지 않았는지 확인한다.
        sendFlatWarmup(trackerId, deviceKey, Instant.now());

        Thread.sleep(300);
        assertThat(findActive(trackerId)).isEmpty();
    }
}
