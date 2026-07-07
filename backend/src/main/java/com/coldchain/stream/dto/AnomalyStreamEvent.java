package com.coldchain.stream.dto;

import com.coldchain.detection.domain.AnomalySeverity;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import java.time.Instant;

public record AnomalyStreamEvent(
        String trackerId,
        AnomalyType type,
        AnomalySeverity severity,
        String message,
        Instant ts,
        AnomalyStatus status) {
}
