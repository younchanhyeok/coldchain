package com.coldchain.prediction.service;

import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.dto.ActivePredictionSummary;
import com.coldchain.prediction.dto.ForecastPoint;
import com.coldchain.prediction.dto.PredictionResponse;
import com.coldchain.prediction.repository.PredictionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * GET /trackers/{id}/prediction — 저장된 상태만 읽는다(Python을 호출하지 않는다). 그래서
 * API 명세의 "503 예측 서버 장애" 케이스는 이 엔드포인트에서 발생할 수 없다 — 예측 시도 자체는
 * 리딩이 들어올 때(PredictionService.analyze)만 일어나고, 조회는 그 결과를 읽을 뿐이다.
 */
@Service
public class PredictionQueryService {

    private final PredictionRepository predictionRepository;

    public PredictionQueryService(PredictionRepository predictionRepository) {
        this.predictionRepository = predictionRepository;
    }

    public PredictionResponse getCurrent(String trackerId) {
        return predictionRepository.findTopByTrackerIdOrderByCreatedAtDesc(trackerId)
                .map(this::toResponse)
                .orElse(PredictionResponse.none());
    }

    /** 트래커 상태(SAFE/CAUTION/RISK/BREACH) 판정용 — tracker 도메인이 service 레이어로 호출한다. */
    public boolean hasActivePrediction(String trackerId) {
        return predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE).isPresent();
    }

    /**
     * 트래커 목록/상세의 activePrediction 필드 — {@link #hasActivePrediction}과 별개 쿼리다.
     * 한 트래커 요약을 만들 때 이 둘을 모두 부르면 같은 조회가 중복되므로, 호출부(TrackerQueryService)는
     * 이 메서드의 결과 하나로 상태 판정(RISK 여부)과 필드 채움을 모두 해결한다.
     */
    public Optional<ActivePredictionSummary> findActiveSummary(String trackerId) {
        return predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE)
                .map(PredictionQueryService::toActiveSummary);
    }

    /** {@link #findActiveSummary}의 배치판 — 목록 화면이 트래커마다 건당 조회하면 N+1이라
     *  IN 한 방으로 가져온다(M6). 활성 예측이 없는 트래커는 맵에 없다. */
    public Map<String, ActivePredictionSummary> findActiveSummaries(Collection<String> trackerIds) {
        if (trackerIds.isEmpty()) {
            return Map.of();
        }
        return predictionRepository.findByTrackerIdInAndStatus(trackerIds, PredictionStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Prediction::getTrackerId, PredictionQueryService::toActiveSummary));
    }

    private static ActivePredictionSummary toActiveSummary(Prediction p) {
        return new ActivePredictionSummary(
                p.getPredictedBreachAt(),
                (int) Duration.between(Instant.now(), p.getPredictedBreachAt()).toMinutes(),
                p.getSlopePerMinute());
    }

    private PredictionResponse toResponse(Prediction prediction) {
        boolean active = prediction.getStatus() == PredictionStatus.ACTIVE;

        // leadTimeMinutes는 "지금부터 남은 분"(응답 전용 의미) — 평가지표의 리드타임
        // (breachedAt-createdAt, "최초 경고가 얼마나 앞섰나")과는 다른 개념이라 혼동하지 않는다.
        Integer leadTimeMinutes = active
                ? (int) Duration.between(Instant.now(), prediction.getPredictedBreachAt()).toMinutes()
                : null;

        // forecast는 저장하지 않고 조회 시점에 anchor+임계값 두 점으로 재생성한다 — 선형회귀라
        // 두 점이면 직선을 완전히 표현한다(중간 점 저장 불필요). ACTIVE가 아니면(취소·무효화·
        // 만료·적중) 더 이상 유효한 예측선이 아니므로 빈 배열.
        List<ForecastPoint> forecast = active
                ? List.of(new ForecastPoint(prediction.getAnchorTs(), prediction.getAnchorTemperature()),
                        new ForecastPoint(prediction.getPredictedBreachAt(), prediction.getThresholdTemp()))
                : List.of();

        return new PredictionResponse(
                prediction.getStatus().name(), prediction.getPredictedBreachAt(), leadTimeMinutes,
                prediction.getThresholdTemp(), prediction.getSlopePerMinute(), prediction.getModelVersion(),
                prediction.getCreatedAt(), forecast);
    }
}
