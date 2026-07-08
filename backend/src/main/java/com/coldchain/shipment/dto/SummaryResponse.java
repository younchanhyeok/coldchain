package com.coldchain.shipment.dto;

// rescuedByPrediction은 M3엔 예측이 없어 항상 0(생략이 아니라 정직한 0) — M4에서 값만 배선 교체.
public record SummaryResponse(
        int totalShipments,
        int inTransit,
        int breachCount,
        int deliveredCount,
        int rescuedByPrediction,
        Integer avgDeliveryMinutes) {
}
