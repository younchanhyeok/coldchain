package com.coldchain.stream.dto;

import com.coldchain.prediction.domain.PredictionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PredictionStreamEvent(
        String trackerId,
        PredictionStatus status,
        Instant predictedBreachAt,
        BigDecimal slopePerMinute,
        String modelVersion,
        Instant createdAt) {
}
