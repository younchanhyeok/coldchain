package com.coldchain.prediction.repository;

import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByTrackerIdAndStatus(String trackerId, PredictionStatus status);

    Optional<Prediction> findTopByTrackerIdOrderByCreatedAtDesc(String trackerId);

    /** EXPIRED 스케줄러 — 예상 이탈 시각을 넘기고도 여전히 ACTIVE인 것들. */
    List<Prediction> findByStatusAndPredictedBreachAtBefore(PredictionStatus status, Instant cutoff);

    /** 평가지표 — 기간 내 생성된 모든 예측 에피소드(TP/FP/리드타임 집계 대상). */
    List<Prediction> findByCreatedAtBetween(Instant from, Instant to);

    /** 평가지표의 missedBreaches 계산용 — breach_event와 매칭할 때는 생성 시각이 아니라
     *  적중 시각(breachedAt) 기준으로 찾아야 한다. */
    List<Prediction> findByStatusAndBreachedAtBetween(PredictionStatus status, Instant from, Instant to);

    /** GET /summary의 rescuedByPrediction — "경고 후 임계 미도달 종료"(취소·만료) 누적 건수. */
    long countByStatusIn(Collection<PredictionStatus> statuses);
}
