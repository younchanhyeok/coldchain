package com.coldchain.prediction.service;

import com.coldchain.prediction.domain.BreachEvent;
import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.dto.PredictionMetricsResponse;
import com.coldchain.prediction.dto.PredictionMetricsResponse.EpisodeSummary;
import com.coldchain.prediction.dto.PredictionMetricsResponse.Period;
import com.coldchain.prediction.repository.BreachEventRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * FR-5 평가지표 — "정확도를 자랑하는 게 아니라 측정한다"의 API 표면. 어드민 전용
 * (GET /admin/metrics/prediction).
 *
 * 지표 정의: TP=BREACHED 에피소드 / FP=CANCELED+EXPIRED 에피소드 / missedBreaches=활성
 * 예측 없이 발생한 이탈. 응답의 리드타임은 breachedAt-createdAt(최초 경고가 실제 이탈보다
 * 얼마나 앞섰나)이고, GET /prediction 응답의 leadTimeMinutes(조회 시점부터 남은 분)와는
 * 다른 개념이다.
 */
@Service
public class PredictionMetricsService {

    private static final long FLAP_GROUPING_MINUTES = 10;

    private final PredictionRepository predictionRepository;
    private final BreachEventRepository breachEventRepository;
    private final TrackerRepository trackerRepository;

    public PredictionMetricsService(PredictionRepository predictionRepository,
            BreachEventRepository breachEventRepository, TrackerRepository trackerRepository) {
        this.predictionRepository = predictionRepository;
        this.breachEventRepository = breachEventRepository;
        this.trackerRepository = trackerRepository;
    }

    public PredictionMetricsResponse getMetrics(Instant from, Instant to, String modelVersion) {
        List<Prediction> episodes = predictionRepository.findByCreatedAtBetween(from, to).stream()
                .filter(p -> modelVersion == null || modelVersion.equals(p.getModelVersion()))
                .toList();

        int truePositives = (int) episodes.stream()
                .filter(p -> p.getStatus() == PredictionStatus.BREACHED)
                .count();
        int falsePositives = (int) episodes.stream()
                .filter(p -> p.getStatus() == PredictionStatus.CANCELED || p.getStatus() == PredictionStatus.EXPIRED)
                .count();

        int concluded = truePositives + falsePositives;
        double falsePositiveRate = concluded > 0 ? (double) falsePositives / concluded : 0.0;
        double hitRate = concluded > 0 ? (double) truePositives / concluded : 0.0;

        List<Long> leadTimesMinutes = episodes.stream()
                .filter(p -> p.getStatus() == PredictionStatus.BREACHED && p.getBreachedAt() != null)
                .map(p -> Duration.between(p.getCreatedAt(), p.getBreachedAt()).toMinutes())
                .sorted()
                .toList();

        Double avgLeadTimeMinutes = average(leadTimesMinutes);
        Double medianLeadTimeMinutes = median(leadTimesMinutes);

        int missedBreaches = countMissedBreaches(from, to);

        List<EpisodeSummary> episodeSummaries = episodes.stream().map(this::toEpisodeSummary).toList();

        return new PredictionMetricsResponse(
                modelVersion, new Period(from, to), episodes.size(), truePositives, falsePositives, missedBreaches,
                round(falsePositiveRate), round(hitRate), avgLeadTimeMinutes, medianLeadTimeMinutes,
                episodeSummaries);
    }

    /**
     * 활성 예측 없이 발생한 이탈(breach_event) 건수 — "경고 없이 이탈". 같은 트래커의 breach_event가
     * {@value #FLAP_GROUPING_MINUTES}분 이내 연속으로 잡히면 임계 경계에서의 진동(flap)일 뿐
     * 하나의 사건이므로 한 번만 센다.
     */
    private int countMissedBreaches(Instant from, Instant to) {
        List<BreachEvent> breachEvents = breachEventRepository.findByTsBetweenOrderByTsAsc(from, to);

        // (trackerId, ts) 정확 일치로 매칭한다 — PredictionService.handleBreach()가 breach_event와
        // Prediction.breachedAt에 같은 event.recordedAt() 값을 그대로 넘기므로 두 Instant는
        // 항상 비트 단위로 같다는 전제다. breachedAt을 이 소스와 독립적으로 다시 계산/보정하는
        // 코드가 생기면 이 매칭은 깨진다 — 그때는 시간 윈도우 매칭으로 바꿔야 한다.
        Set<String> predictedKeys = predictionRepository
                .findByStatusAndBreachedAtBetween(PredictionStatus.BREACHED, from, to).stream()
                .map(p -> p.getTrackerId() + "@" + p.getBreachedAt())
                .collect(HashSet::new, Set::add, Set::addAll);

        Map<String, Instant> lastMissedByTracker = new HashMap<>();
        int missed = 0;
        for (BreachEvent event : breachEvents) {
            if (predictedKeys.contains(event.getTrackerId() + "@" + event.getTs())) {
                continue; // 예측이 실제로 경고했던 이탈 — missed 아님
            }
            Instant lastMissed = lastMissedByTracker.get(event.getTrackerId());
            if (lastMissed != null && Duration.between(lastMissed, event.getTs()).toMinutes() < FLAP_GROUPING_MINUTES) {
                continue; // 같은 사건의 진동 — 이미 셌다
            }
            missed++;
            lastMissedByTracker.put(event.getTrackerId(), event.getTs());
        }
        return missed;
    }

    // PERF(M6): N+1 — episodes마다 tracker를 findById로 건당 조회한다(findAllById로 배치
    // 가능). 어드민 전용·저빈도 호출이라 지금은 무시 가능한 수준 — 부하테스트에서 실측 후 고친다.
    private EpisodeSummary toEpisodeSummary(Prediction prediction) {
        String productName = trackerRepository.findById(prediction.getTrackerId())
                .map(tracker -> tracker.getProductName())
                .orElse(null);
        Integer leadTimeMinutes = prediction.getStatus() == PredictionStatus.BREACHED && prediction.getBreachedAt() != null
                ? (int) Duration.between(prediction.getCreatedAt(), prediction.getBreachedAt()).toMinutes()
                : null;
        return new EpisodeSummary(prediction.getTrackerId(), productName, prediction.getStatus().name(), leadTimeMinutes);
    }

    private Double average(List<Long> values) {
        return values.isEmpty() ? null : values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private Double median(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int n = sortedValues.size();
        if (n % 2 == 1) {
            return (double) sortedValues.get(n / 2);
        }
        return (sortedValues.get(n / 2 - 1) + sortedValues.get(n / 2)) / 2.0;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
