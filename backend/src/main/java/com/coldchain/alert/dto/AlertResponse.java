package com.coldchain.alert.dto;

import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.domain.AlertChannel;
import com.coldchain.alert.domain.AlertSeverity;
import com.coldchain.alert.domain.AlertStatus;
import com.coldchain.alert.domain.AlertType;
import java.math.BigDecimal;
import java.time.Instant;

public record AlertResponse(
        Long id,
        String trackerId,
        AlertType type,
        AlertSeverity severity,
        BigDecimal temperatureAtEvent,
        String message,
        AlertChannel channel,
        AlertStatus status,
        int retryCount,
        Instant createdAt) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(), alert.getTrackerId(), alert.getType(), alert.getSeverity(),
                alert.getTemperatureAtEvent(), alert.getMessage(), alert.getChannel(), alert.getStatus(),
                alert.getRetryCount(), alert.getCreatedAt());
    }
}
