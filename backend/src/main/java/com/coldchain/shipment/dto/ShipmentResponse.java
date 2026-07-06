package com.coldchain.shipment.dto;

import com.coldchain.shipment.domain.ShipmentStatus;

// M1: magicLink는 아직 발급하지 않는다(M5에서 GET /track/{token}과 함께 도입).
public record ShipmentResponse(Long shipmentId, ShipmentStatus status) {
}
