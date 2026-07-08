package com.coldchain.shipment.dto;

import java.util.List;

public record ShipmentListResponse(List<ShipmentSummaryResponse> content, int page, int size, long totalElements) {
}
