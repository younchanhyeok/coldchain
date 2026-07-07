package com.coldchain.tracker.dto;

import java.time.Instant;

public record BreachPointResponse(double lat, double lon, Instant ts) {
}
