package com.coldchain.tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TrackerRegisterRequest(
        @NotBlank String trackerId,
        @NotBlank String productName,
        @NotNull BigDecimal thresholdTemp) {
}
