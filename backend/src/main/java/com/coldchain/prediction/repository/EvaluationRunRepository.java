package com.coldchain.prediction.repository;

import com.coldchain.prediction.domain.EvaluationRun;
import com.coldchain.prediction.domain.EvaluationRun.TriggerType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {

    List<EvaluationRun> findByOrderByCreatedAtDesc(Pageable pageable);

    /** 스케줄 런 멱등 가드 — 같은 (기간, 모델버전) SCHEDULED 런이 이미 있는지. modelVersion은
     *  스케줄 경로에서 항상 non-null(존재하는 버전별 1건)이라 derived query로 충분. */
    boolean existsByPeriodStartAndPeriodEndAndModelVersionAndTriggerType(
            Instant periodStart, Instant periodEnd, String modelVersion, TriggerType triggerType);
}
