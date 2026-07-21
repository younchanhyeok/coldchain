package com.coldchain.ingest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReadingIngestRequest(
        @NotNull Double temperature,
        @NotNull Double lat,
        @NotNull Double lon,
        @NotNull Instant recordedAt,
        Long seq,
        Double ambientTemp) {
}
