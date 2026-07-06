package com.coldchain.tracker.dto;

import java.util.List;

public record TrackerListResponse(List<TrackerSummaryResponse> content, int page, int size, long totalElements) {
}
