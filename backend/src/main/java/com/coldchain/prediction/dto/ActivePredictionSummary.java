package com.coldchain.prediction.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 트래커 목록/상세의 activePrediction 필드 — 위험 모니터링 탭 임박순 리스트가 이 값으로 정렬한다. */
public record ActivePredictionSummary(
        Instant predictedBreachAt,
        Integer leadTimeMinutes,
        BigDecimal slopePerMinute) {
}
