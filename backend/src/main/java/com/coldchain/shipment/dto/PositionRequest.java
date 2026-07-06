package com.coldchain.shipment.dto;

import jakarta.validation.constraints.NotNull;

public record PositionRequest(@NotNull Double lat, @NotNull Double lon, String name) {
}
