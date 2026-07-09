package com.coldchain.prediction.event;

import com.coldchain.prediction.domain.PredictionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 에피소드 생성(ACTIVE)·취소·무효화·만료·적중 전이 시에만 발행된다 — 리딩마다 반복되는
 * 단순 갱신(refresh)에는 발행하지 않는다(anomaly의 억제 패턴과 동일 — 매 리딩 재알림 방지).
 */
public record PredictionChangedEvent(
        Long id,
        String trackerId,
        PredictionStatus status,
        Instant predictedBreachAt,
        BigDecimal slopePerMinute,
        String modelVersion,
        Instant createdAt) {
}
