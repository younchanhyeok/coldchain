package com.coldchain.stream.dto;

import com.coldchain.tracker.domain.TrackerStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ReadingStreamEvent(
        String trackerId,
        BigDecimal temperature,
        Double lat,
        Double lon,
        Instant ts,
        TrackerStatus status) {
}
