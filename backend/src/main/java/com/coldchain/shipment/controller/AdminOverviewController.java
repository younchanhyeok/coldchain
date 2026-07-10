package com.coldchain.shipment.controller;

import com.coldchain.auth.AdminKeyValidator;
import com.coldchain.common.error.AdminUnauthorizedException;
import com.coldchain.shipment.dto.AdminOverviewResponse;
import com.coldchain.shipment.service.AdminOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;
    private final AdminKeyValidator adminKeyValidator;

    public AdminOverviewController(AdminOverviewService adminOverviewService, AdminKeyValidator adminKeyValidator) {
        this.adminOverviewService = adminOverviewService;
        this.adminKeyValidator = adminKeyValidator;
    }

    @GetMapping("/api/v1/admin/overview")
    public AdminOverviewResponse getOverview(@RequestHeader(value = "X-Admin-Key", required = false) String adminKey) {
        if (!adminKeyValidator.isValid(adminKey)) {
            throw new AdminUnauthorizedException("어드민 키가 유효하지 않습니다.");
        }
        return adminOverviewService.getOverview();
    }
}
