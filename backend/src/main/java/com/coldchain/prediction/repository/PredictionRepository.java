package com.coldchain.prediction.repository;

import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByTrackerIdAndStatus(String trackerId, PredictionStatus status);

    /** 목록 화면 배치 조회 — 트래커별 건당 조회(N+1)를 IN 한 방으로. M6 부하테스트에서
     *  대시보드 조회를 5000 트래커 기준 22.7초까지 밀리게 한 왕복 중 하나. */
    List<Prediction> findByTrackerIdInAndStatus(Collection<String> trackerIds, PredictionStatus status);

    Optional<Prediction> findTopByTrackerIdOrderByCreatedAtDesc(String trackerId);

    /** EXPIRED 스케줄러 — 예상 이탈 시각을 넘기고도 여전히 ACTIVE인 것들. */
    List<Prediction> findByStatusAndPredictedBreachAtBefore(PredictionStatus status, Instant cutoff);

    /** 평가지표 — 기간 내 생성된 모든 예측 에피소드(TP/FP/리드타임 집계 대상). */
    List<Prediction> findByCreatedAtBetween(Instant from, Instant to);

    /** 평가 런 자동화(M7) — 기간 내 존재하는 모델버전 목록. 버전별로 1건씩 스냅샷을 만든다.
     *  경계는 getMetrics의 findByCreatedAtBetween(폐구간 [from,to])과 일치시킨다 — 버전 탐색과
     *  지표 집계가 같은 창을 봐야 누락 없이 정합한다. */
    @Query("SELECT DISTINCT p.modelVersion FROM Prediction p "
            + "WHERE p.createdAt >= :from AND p.createdAt <= :to AND p.modelVersion IS NOT NULL")
    List<String> findDistinctModelVersions(@Param("from") Instant from, @Param("to") Instant to);

    /** 평가지표의 missedBreaches 계산용 — breach_event와 매칭할 때는 생성 시각이 아니라
     *  적중 시각(breachedAt) 기준으로 찾아야 한다. */
    List<Prediction> findByStatusAndBreachedAtBetween(PredictionStatus status, Instant from, Instant to);

    /** GET /summary의 rescuedByPrediction — "경고 후 임계 미도달 종료"(취소·만료) 누적 건수. */
    long countByStatusIn(Collection<PredictionStatus> statuses);
}
