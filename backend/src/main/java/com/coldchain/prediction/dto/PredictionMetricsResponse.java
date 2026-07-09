package com.coldchain.prediction.dto;

import java.time.Instant;
import java.util.List;

public record PredictionMetricsResponse(
        String modelVersion,
        Period period,
        int totalPredictions,
        int truePositives,
        int falsePositives,
        int missedBreaches,
        double falsePositiveRate,
        double hitRate,
        Double avgLeadTimeMinutes,
        Double medianLeadTimeMinutes,
        List<EpisodeSummary> episodes) {

    public record Period(Instant from, Instant to) {
    }

    /** 리포트 탭 "시나리오 결과 테이블"의 행 하나 — status는 PredictionStatus.name() 그대로.
     * createdAt은 에피소드 최초 경고 시각(불변) — "언제 발생했나"의 기준. */
    public record EpisodeSummary(
            String trackerId, String productName, String status, Integer leadTimeMinutes, Instant createdAt) {
    }
}
