package com.coldchain.tracker.dto;

import com.coldchain.shipment.domain.ShipmentStatus;

public record ShipmentSummary(
        PositionResponse origin,
        PositionResponse destination,
        String consigneeName,
        String driverContact,
        ShipmentStatus status) {
}
