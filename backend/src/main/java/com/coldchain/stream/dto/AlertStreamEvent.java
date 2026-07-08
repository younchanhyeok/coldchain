package com.coldchain.stream.dto;

import com.coldchain.alert.domain.AlertSeverity;
import com.coldchain.alert.domain.AlertStatus;
import com.coldchain.alert.domain.AlertType;
import java.time.Instant;

public record AlertStreamEvent(
        Long id,
        String trackerId,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        Instant createdAt) {
}
