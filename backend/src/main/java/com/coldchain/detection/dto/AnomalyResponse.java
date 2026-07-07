package com.coldchain.detection.dto;

import com.coldchain.detection.domain.AnomalyEvent;
import com.coldchain.detection.domain.AnomalySeverity;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import java.math.BigDecimal;
import java.time.Instant;

public record AnomalyResponse(
        Long id,
        Instant ts,
        AnomalyType type,
        AnomalySeverity severity,
        String message,
        BigDecimal zScore,
        AnomalyStatus status) {

    public static AnomalyResponse from(AnomalyEvent event) {
        return new AnomalyResponse(
                event.getId(), event.getTs(), event.getType(), event.getSeverity(), event.getMessage(),
                event.getZScore(), event.getStatus());
    }
}
