package com.coldchain.reading.dto;

import java.time.Instant;

public record ReadingPoint(Instant ts, Double temperature, Double lat, Double lon) {
}
