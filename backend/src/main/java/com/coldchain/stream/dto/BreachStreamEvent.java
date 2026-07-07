package com.coldchain.stream.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BreachStreamEvent(
        String trackerId,
        BigDecimal temperature,
        BigDecimal thresholdTemp,
        Instant ts) {
}
