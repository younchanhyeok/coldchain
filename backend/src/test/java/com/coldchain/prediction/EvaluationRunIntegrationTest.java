package com.coldchain.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.repository.EvaluationRunRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.prediction.service.EvaluationRunService;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 평가 런(M7) — 스냅샷 정확도(getMetrics 재사용), 스케줄 멱등, 빈 창 스킵, 어드민 인증, 시각오차 지표.
 * 예측은 도메인 팩토리로 직접 시드(createdAt을 ts 파라미터로 제어해 창 안에 넣는다).
 */
@SpringBootTest(properties = "app.auth.admin-key=test-admin-key")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvaluationRunIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private EvaluationRunRepository evaluationRunRepository;

    @Autowired
    private EvaluationRunService evaluationRunService;

    @Autowired
    private TrackerRepository trackerRepository;

    private void seedTracker(String trackerId) {
        // 예측 시드는 tracker FK를 요구 — 리포지토리로 직접 저장(등록 API는 화주 JWT가 필요).
        trackerRepository.save(new Tracker(trackerId, 1L, "백신 A (test)", new BigDecimal("8.0"), "hash"));
    }

    /** 적중(BREACHED) 예측 — createdAt=created, 예측시각 predicted, 실제 이탈 breached. */
    private Prediction breachedPrediction(String trackerId, String modelVersion, Instant created,
            Instant predicted, Instant breached) {
        Prediction p = Prediction.activate(trackerId, created, new BigDecimal("8.0"),
                predicted, new BigDecimal("0.5"), modelVersion, new BigDecimal("5.0"));
        p.markBreached(breached);
        return predictionRepository.save(p);
    }

    @Test
    void manualRunSnapshotMatchesLiveMetrics() throws Exception {
        String tracker = "TRK-EVAL-1";
        seedTracker(tracker);
        Instant from = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant to = Instant.now().plus(30, ChronoUnit.MINUTES);
        Instant created = Instant.now();
        // 예측 시각과 실제 이탈이 3분 차 → avgBreachTimingErrorMinutes ≈ 3
        breachedPrediction(tracker, "v2-newton", created, created.plus(10, ChronoUnit.MINUTES),
                created.plus(13, ChronoUnit.MINUTES));

        String body = mockMvc.perform(post("/api/v1/admin/evaluation-runs")
                        .header("X-Admin-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("""
                                {"from":"%s","to":"%s","label":"m7-test","modelVersion":"v2-newton"}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("m7-test"))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.modelVersion").value("v2-newton"))
                .andExpect(jsonPath("$.truePositives").value(1))
                .andExpect(jsonPath("$.avgBreachTimingErrorMinutes").value(3.0))
                .andReturn().getResponse().getContentAsString();

        // 스냅샷이 라이브 getMetrics와 같은 값인지 — 같은 창을 metrics API로 재조회해 대조.
        mockMvc.perform(get("/api/v1/admin/metrics/prediction")
                        .header("X-Admin-Key", "test-admin-key")
                        .param("from", from.toString()).param("to", to.toString())
                        .param("modelVersion", "v2-newton"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truePositives").value(1))
                .andExpect(jsonPath("$.avgBreachTimingErrorMinutes").value(3.0));

        assertThat(body).contains("\"m7-test\"");
    }

    @Test
    void scheduledSnapshotIsIdempotentForSameWindow() throws Exception {
        String tracker = "TRK-EVAL-2";
        seedTracker(tracker);
        Instant from = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant to = Instant.now().plus(30, ChronoUnit.MINUTES);
        Instant created = Instant.now();
        breachedPrediction(tracker, "v1-linear", created, created.plus(5, ChronoUnit.MINUTES),
                created.plus(6, ChronoUnit.MINUTES));

        evaluationRunService.snapshotScheduled(from, to);
        evaluationRunService.snapshotScheduled(from, to); // 재실행 — 중복 생성 안 됨

        long v1Runs = evaluationRunRepository.findAll().stream()
                .filter(r -> "v1-linear".equals(r.getModelVersion())
                        && r.getPeriodStart().equals(from) && r.getPeriodEnd().equals(to))
                .count();
        assertThat(v1Runs).isEqualTo(1);
    }

    @Test
    void scheduledSnapshotSkipsEmptyWindow() {
        // 예측이 하나도 없는 과거 창 → 빈 런 도배 안 함.
        Instant from = Instant.parse("2000-01-01T00:00:00Z");
        Instant to = Instant.parse("2000-01-01T01:00:00Z");
        long before = evaluationRunRepository.count();

        evaluationRunService.snapshotScheduled(from, to);

        assertThat(evaluationRunRepository.count()).isEqualTo(before);
    }

    @Test
    void listReturnsRunsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evaluation-runs")
                        .header("X-Admin-Key", "test-admin-key").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rejectsWithoutAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evaluation-runs"))
                .andExpect(status().isUnauthorized());
    }
}
