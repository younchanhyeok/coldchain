package com.coldchain.alert.controller;

import com.coldchain.alert.domain.Alert;
import com.coldchain.alert.dto.AlertListResponse;
import com.coldchain.alert.dto.AlertResponse;
import com.coldchain.alert.repository.AlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 탭 + 화물 관리 상세 패널 공용 — trackerId로 필터링 가능한 알림 발송 이력 조회.
 * M5 이전이라 화주(shipper) 스코핑 없음 — 다른 화주-JWT 엔드포인트와 마찬가지로 지금은
 * 단일 dev shipper뿐이라 실질적 데이터 누출은 없음. JWT 인증 도입 시 함께 스코핑할 것.
 */
@RestController
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/api/v1/alerts")
    public AlertListResponse list(
            @RequestParam(required = false) String trackerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> result = trackerId != null
                ? alertRepository.findByTrackerIdOrderByCreatedAtDesc(trackerId, pageable)
                : alertRepository.findAllByOrderByCreatedAtDesc(pageable);

        return new AlertListResponse(
                result.getContent().stream().map(AlertResponse::from).toList(),
                page, size, result.getTotalElements());
    }
}
