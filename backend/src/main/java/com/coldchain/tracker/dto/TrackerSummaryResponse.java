package com.coldchain.tracker.dto;

import com.coldchain.prediction.dto.ActivePredictionSummary;
import com.coldchain.tracker.domain.TrackerStatus;
import java.math.BigDecimal;
import java.time.Instant;

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
        ActivePredictionSummary activePrediction) {
}
