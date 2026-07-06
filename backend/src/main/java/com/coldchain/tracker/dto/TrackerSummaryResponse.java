package com.coldchain.tracker.dto;

import com.coldchain.tracker.domain.TrackerStatus;
import java.math.BigDecimal;
import java.time.Instant;

// M1: activePrediction은 항상 null(M4에서 실제 예측 연동).
public record TrackerSummaryResponse(
        String trackerId,
        Long shipmentId,
        String productName,
        String originName,
        String destinationName,
        BigDecimal thresholdTemp,
        TrackerStatus status,
        BigDecimal lastTemperature,
        PositionResponse lastPosition,
        Instant lastReportedAt,
        Object activePrediction) {
}
