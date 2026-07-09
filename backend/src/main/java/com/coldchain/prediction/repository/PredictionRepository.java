package com.coldchain.prediction.repository;

import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByTrackerIdAndStatus(String trackerId, PredictionStatus status);

    Optional<Prediction> findTopByTrackerIdOrderByCreatedAtDesc(String trackerId);

    /** EXPIRED 스케줄러 — 예상 이탈 시각을 넘기고도 여전히 ACTIVE인 것들. */
    List<Prediction> findByStatusAndPredictedBreachAtBefore(PredictionStatus status, Instant cutoff);

    /** 평가지표(M4 PR3) — 기간 내 생성된 모든 예측 에피소드. */
    List<Prediction> findByCreatedAtBetween(Instant from, Instant to);
}
