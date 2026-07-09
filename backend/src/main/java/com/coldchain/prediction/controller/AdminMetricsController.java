package com.coldchain.prediction.controller;

import com.coldchain.auth.AdminKeyValidator;
import com.coldchain.common.error.AdminUnauthorizedException;
import com.coldchain.prediction.dto.PredictionMetricsResponse;
import com.coldchain.prediction.service.PredictionMetricsService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminMetricsController {

    private final PredictionMetricsService predictionMetricsService;
    private final AdminKeyValidator adminKeyValidator;

    public AdminMetricsController(PredictionMetricsService predictionMetricsService,
            AdminKeyValidator adminKeyValidator) {
        this.predictionMetricsService = predictionMetricsService;
        this.adminKeyValidator = adminKeyValidator;
    }

    @GetMapping("/api/v1/admin/metrics/prediction")
    public PredictionMetricsResponse getPredictionMetrics(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String modelVersion) {
        if (!adminKeyValidator.isValid(adminKey)) {
            throw new AdminUnauthorizedException("어드민 키가 유효하지 않습니다.");
        }
        return predictionMetricsService.getMetrics(from, to, modelVersion);
    }
}
