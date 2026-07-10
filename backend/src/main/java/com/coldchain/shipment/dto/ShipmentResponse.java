package com.coldchain.shipment.dto;

import com.coldchain.shipment.domain.ShipmentStatus;

// magicLink는 생성(POST) 응답에서만 채워진다 — PATCH(상태 전이) 응답은 null(명세에 없음,
// 재발급 개념이 아니라 최초 생성 시 1회 발급이므로 매번 돌려줄 이유가 없다).
public record ShipmentResponse(Long shipmentId, ShipmentStatus status, String magicLink) {
}
