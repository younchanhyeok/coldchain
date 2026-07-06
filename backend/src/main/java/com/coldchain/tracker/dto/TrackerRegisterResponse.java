package com.coldchain.tracker.dto;

import java.time.Instant;

public record TrackerRegisterResponse(String trackerId, String deviceKey, Instant createdAt) {
}
