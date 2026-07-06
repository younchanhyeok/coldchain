package com.coldchain.shipment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShipmentCreateRequest(
        @NotBlank String trackerId,
        @NotBlank String productName,
        @NotNull @Valid PositionRequest origin,
        @NotNull @Valid PositionRequest destination,
        @Valid ConsigneeRequest consignee,
        String driverContact) {
}
