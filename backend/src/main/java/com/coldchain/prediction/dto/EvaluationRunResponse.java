package com.coldchain.prediction.dto;

import com.coldchain.prediction.domain.EvaluationRun;
import java.time.Instant;

/** 평가 런 조회 응답(M7) — 어드민 리포트 뷰가 v1/v2 런을 나란히 비교하는 데이터. */
public record EvaluationRunResponse(
        Long id,
        String label,
        Instant periodStart,
        Instant periodEnd,
        String modelVersion,
        String triggerType,
        int totalPredictions,
        int truePositives,
        int falsePositives,
        int missedBreaches,
        double hitRate,
        double falsePositiveRate,
        Double avgLeadTimeMinutes,
        Double medianLeadTimeMinutes,
        Double avgBreachTimingErrorMinutes,
        Instant createdAt) {

    public static EvaluationRunResponse from(EvaluationRun run) {
        return new EvaluationRunResponse(
                run.getId(), run.getLabel(), run.getPeriodStart(), run.getPeriodEnd(), run.getModelVersion(),
                run.getTriggerType().name(), run.getTotalPredictions(), run.getTruePositives(),
                run.getFalsePositives(), run.getMissedBreaches(), run.getHitRate(), run.getFalsePositiveRate(),
                run.getAvgLeadTimeMinutes(), run.getMedianLeadTimeMinutes(), run.getAvgBreachTimingErrorMinutes(),
                run.getCreatedAt());
    }
}
