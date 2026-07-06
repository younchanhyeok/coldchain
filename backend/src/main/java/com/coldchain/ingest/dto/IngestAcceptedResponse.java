package com.coldchain.ingest.dto;

import java.time.Instant;

public record IngestAcceptedResponse(boolean accepted, Instant serverTs) {
}
