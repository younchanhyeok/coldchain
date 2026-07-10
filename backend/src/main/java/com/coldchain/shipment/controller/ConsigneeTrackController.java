package com.coldchain.shipment.controller;

import com.coldchain.shipment.dto.ConsigneeTrackResponse;
import com.coldchain.shipment.service.ConsigneeTrackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 매직링크 — 계정·로그인 없음, 토큰이 곧 인가 스코프(shipment 1건). SecurityConfig에서 permitAll. */
@RestController
public class ConsigneeTrackController {

    private final ConsigneeTrackService consigneeTrackService;

    public ConsigneeTrackController(ConsigneeTrackService consigneeTrackService) {
        this.consigneeTrackService = consigneeTrackService;
    }

    @GetMapping("/api/v1/track/{token}")
    public ConsigneeTrackResponse getTrack(@PathVariable String token) {
        return consigneeTrackService.getTrack(token);
    }
}
