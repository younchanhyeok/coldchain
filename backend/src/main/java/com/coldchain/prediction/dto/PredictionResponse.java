package com.coldchain.prediction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * status는 도메인 enum(PredictionStatus)이 아니라 문자열이다 — "NONE"(에피소드 없음)은
 * 실존 행이 없는 상태라 도메인 enum에 억지로 끼워 넣지 않는다.
 */
public record PredictionResponse(
        String status,
        Instant predictedBreachAt,
        Integer leadTimeMinutes,
        BigDecimal thresholdTemp,
        BigDecimal slopePerMinute,
        String modelVersion,
        Instant createdAt,
        List<ForecastPoint> forecast) {

    public static PredictionResponse none() {
        return new PredictionResponse("NONE", null, null, null, null, null, null, List.of());
    }
}
