package com.coldchain.alert.controller;

import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.dto.AlertListResponse;
import com.coldchain.alert.dto.AlertResponse;
import com.coldchain.alert.repository.AlertRepository;
import com.coldchain.auth.AuthenticatedUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 탭 + 화물 관리 상세 패널 공용 — trackerId로 필터링 가능한 알림 발송 이력 조회. 화주 스코핑(M5). */
@RestController
public class AlertController {

    private final AlertRepository alertRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AlertController(AlertRepository alertRepository, AuthenticatedUserProvider authenticatedUserProvider) {
        this.alertRepository = alertRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @GetMapping("/api/v1/alerts")
    public AlertListResponse list(
            @RequestParam(required = false) String trackerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result = alertRepository.findByShipperIdAndOptionalTrackerId(
                authenticatedUserProvider.shipperId(), trackerId, pageable);

        return new AlertListResponse(
                result.getContent().stream().map(AlertResponse::from).toList(),
                page, size, result.getTotalElements());
    }
}
