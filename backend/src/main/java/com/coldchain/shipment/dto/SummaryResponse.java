package com.coldchain.shipment.dto;

// rescuedByPrediction(M4~): 예측 경고 후 실제로는 임계에 도달하지 않고 종료된(취소·만료)
// 누적 건수 — "구조된 박스". M5 이전이라 화주 스코핑 없이 시스템 전체 집계(alert 등 다른
// M3 API와 동일한 전제).
public record SummaryResponse(
        int totalShipments,
        int inTransit,
        int breachCount,
        int deliveredCount,
        int rescuedByPrediction,
        Integer avgDeliveryMinutes) {
}
