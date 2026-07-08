package com.coldchain.tracker.dto;

import com.coldchain.detection.dto.AnomalyResponse;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record TrackerDetailResponse(
        @JsonUnwrapped TrackerSummaryResponse summary,
        ShipmentSummary shipment,
        List<AnomalyResponse> activeAnomalies) {
}
