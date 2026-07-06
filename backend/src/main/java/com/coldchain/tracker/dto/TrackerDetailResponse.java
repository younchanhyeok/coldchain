package com.coldchain.tracker.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

// M1: activeAnomalies는 항상 빈 리스트(M3에서 실제 이상탐지 연동).
public record TrackerDetailResponse(
        @JsonUnwrapped TrackerSummaryResponse summary,
        ShipmentSummary shipment,
        List<Object> activeAnomalies) {
}
