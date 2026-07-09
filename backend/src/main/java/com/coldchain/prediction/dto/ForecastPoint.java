package com.coldchain.prediction.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ForecastPoint(Instant ts, BigDecimal temperature) {
}
