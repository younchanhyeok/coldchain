package com.coldchain.shipment.dto;

import com.coldchain.shipment.domain.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record ShipmentStatusUpdateRequest(@NotNull ShipmentStatus status) {
}
