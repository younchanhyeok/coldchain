package com.coldchain.shipment.service;

import com.coldchain.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;

/**
 * 트래커의 활성 배송 목적지까지 남은 거리 조회 — 예측(L3) 도메인이 잔여거리 변수를 얻는 창구(M7).
 * 도메인 간 참조는 service 레이어를 통해서만(CLAUDE.md) — prediction이 ShipmentRepository를
 * 직접 참조하지 않게 이 서비스가 감싼다. 화주 스코핑 없음(내부 시스템 호출, trackerId로 이미 특정).
 */
@Service
public class ShipmentDistanceService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentDistanceService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    /** 활성 배송·목적지 좌표가 없으면 null. */
    public Double remainingDistanceMeters(String trackerId, double lat, double lon) {
        return shipmentRepository.findRemainingDistanceMeters(trackerId, lat, lon);
    }
}
