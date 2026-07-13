package com.coldchain.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.coldchain.prediction.domain.BreachEvent;
import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.dto.PredictionMetricsResponse;
import com.coldchain.prediction.repository.BreachEventRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.prediction.service.PredictionMetricsService;
import com.coldchain.tracker.repository.TrackerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 순수 계산 로직(TP/FP/리드타임/missedBreaches 플랩 묶기) 검증 — Testcontainers 없이
 * 리포지토리를 Mockito로 대체한다(계산 로직 자체엔 DB가 필요 없음). 원하는 상태의 Prediction은
 * reflection이 아니라 실제 라이프사이클 메서드(activate→cancel/expire/markBreached)로 만든다.
 */
@ExtendWith(MockitoExtension.class)
class PredictionMetricsServiceTest {

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private BreachEventRepository breachEventRepository;

    @Mock
    private TrackerRepository trackerRepository;

    private final Instant base = Instant.parse("2026-01-01T00:00:00Z");

    private PredictionMetricsService newService() {
        return new PredictionMetricsService(predictionRepository, breachEventRepository, trackerRepository);
    }

    private Prediction newActive(String trackerId, Instant createdAt) {
        return Prediction.activate(trackerId, createdAt, BigDecimal.valueOf(8.0), createdAt.plusSeconds(600),
                BigDecimal.valueOf(0.1), "v1-linear", BigDecimal.valueOf(4.0));
    }

    private Prediction breached(String trackerId, Instant createdAt, Instant breachedAt) {
        Prediction prediction = newActive(trackerId, createdAt);
        prediction.markBreached(breachedAt);
        return prediction;
    }

    private Prediction canceled(String trackerId, Instant createdAt) {
        Prediction prediction = newActive(trackerId, createdAt);
        prediction.cancel();
        return prediction;
    }

    private Prediction expired(String trackerId, Instant createdAt) {
        Prediction prediction = newActive(trackerId, createdAt);
        prediction.expire();
        return prediction;
    }

    @Test
    void countsTruePositivesAndFalsePositivesAndIgnoresStillActiveEpisodes() {
        Instant from = base;
        Instant to = base.plus(1, ChronoUnit.DAYS);

        List<Prediction> episodes = List.of(
                breached("TRK-1", base, base.plusSeconds(600)),
                breached("TRK-2", base, base.plusSeconds(1200)),
                canceled("TRK-3", base),
                expired("TRK-4", base),
                newActive("TRK-5", base)); // 아직 진행 중 — TP/FP 어느 쪽도 아님

        when(predictionRepository.findByCreatedAtBetween(from, to)).thenReturn(episodes);
        when(predictionRepository.findByStatusAndBreachedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(breachEventRepository.findByTsBetweenOrderByTsAsc(from, to)).thenReturn(List.of());

        PredictionMetricsResponse response = newService().getMetrics(from, to, null);

        assertThat(response.totalPredictions()).isEqualTo(5);
        assertThat(response.truePositives()).isEqualTo(2);
        assertThat(response.falsePositives()).isEqualTo(2);
        assertThat(response.hitRate()).isEqualTo(0.5);
        assertThat(response.falsePositiveRate()).isEqualTo(0.5);
    }

    @Test
    void computesAverageAndMedianLeadTimeFromBreachedEpisodesOnly() {
        Instant from = base;
        Instant to = base.plus(1, ChronoUnit.DAYS);

        List<Prediction> episodes = List.of(
                breached("TRK-1", base, base.plusSeconds(600)),  // 10분
                breached("TRK-2", base, base.plusSeconds(1200)), // 20분
                breached("TRK-3", base, base.plusSeconds(1800))); // 30분

        when(predictionRepository.findByCreatedAtBetween(from, to)).thenReturn(episodes);
        when(predictionRepository.findByStatusAndBreachedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(breachEventRepository.findByTsBetweenOrderByTsAsc(from, to)).thenReturn(List.of());

        PredictionMetricsResponse response = newService().getMetrics(from, to, null);

        assertThat(response.avgLeadTimeMinutes()).isEqualTo(20.0);
        assertThat(response.medianLeadTimeMinutes()).isEqualTo(20.0);
    }

    @Test
    void countsMissedBreachOnlyWhenNoActivePredictionCoveredIt() {
        Instant from = base;
        Instant to = base.plus(1, ChronoUnit.DAYS);

        BreachEvent predictedBreach = new BreachEvent("TRK-PREDICTED", base.plusSeconds(600));
        BreachEvent missedBreach = new BreachEvent("TRK-MISSED", base.plusSeconds(1200));
        Prediction predictedEpisode = breached("TRK-PREDICTED", base, base.plusSeconds(600));

        when(predictionRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of(predictedEpisode));
        when(predictionRepository.findByStatusAndBreachedAtBetween(PredictionStatus.BREACHED, from, to))
                .thenReturn(List.of(predictedEpisode));
        when(breachEventRepository.findByTsBetweenOrderByTsAsc(from, to))
                .thenReturn(List.of(predictedBreach, missedBreach));

        PredictionMetricsResponse response = newService().getMetrics(from, to, null);

        assertThat(response.missedBreaches()).isEqualTo(1);
    }

    @Test
    void groupsFlappingBreachEventsWithinTenMinutesAsOneMissedIncident() {
        Instant from = base;
        Instant to = base.plus(1, ChronoUnit.DAYS);

        // 같은 트래커가 5분 간격으로 2번 이탈(경계 진동) — 하나의 미탐 사건으로 묶여야 한다.
        List<BreachEvent> flapping = List.of(
                new BreachEvent("TRK-FLAP", base),
                new BreachEvent("TRK-FLAP", base.plusSeconds(300)));

        when(predictionRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of());
        when(predictionRepository.findByStatusAndBreachedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(breachEventRepository.findByTsBetweenOrderByTsAsc(from, to)).thenReturn(flapping);

        PredictionMetricsResponse response = newService().getMetrics(from, to, null);

        assertThat(response.missedBreaches()).isEqualTo(1);
    }

    @Test
    void filtersEpisodesByModelVersion() {
        Instant from = base;
        Instant to = base.plus(1, ChronoUnit.DAYS);

        Prediction v1Episode = breached("TRK-V1", base, base.plusSeconds(600));
        Prediction v2Episode = Prediction.activate("TRK-V2", base, BigDecimal.valueOf(8.0), base.plusSeconds(600),
                BigDecimal.valueOf(0.1), "v2-multivariate", BigDecimal.valueOf(4.0));
        v2Episode.markBreached(base.plusSeconds(600));

        when(predictionRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of(v1Episode, v2Episode));
        when(predictionRepository.findByStatusAndBreachedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(breachEventRepository.findByTsBetweenOrderByTsAsc(from, to)).thenReturn(List.of());

        PredictionMetricsResponse response = newService().getMetrics(from, to, "v1-linear");

        assertThat(response.totalPredictions()).isEqualTo(1);
        assertThat(response.episodes()).hasSize(1);
        assertThat(response.episodes().get(0).trackerId()).isEqualTo("TRK-V1");
    }
}
