package com.coldchain.reading.dto;

import java.time.Instant;
import java.util.List;

public record ReadingSeriesResponse(String trackerId, List<ReadingPoint> readings, Instant nextBefore) {
}
