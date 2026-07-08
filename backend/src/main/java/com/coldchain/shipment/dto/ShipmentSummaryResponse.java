package com.coldchain.shipment.dto;

import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.tracker.domain.TrackerStatus;
import com.coldchain.tracker.dto.PositionResponse;
import java.math.BigDecimal;
import java.time.Instant;

// trackerStatus는 shipmentStatus와 무관하게 트래커의 최신 상태를 그대로 담는다(참고용) —
// 화면에서 배송 완료(shipmentStatus=DELIVERED) 화물은 트래커 상태 뱃지 대신 "완료"를 우선 표시한다.
public record ShipmentSummaryResponse(
        Long shipmentId,
        String trackerId,
        String productName,
        String originName,
        String destinationName,
        ShipmentStatus shipmentStatus,
        TrackerStatus trackerStatus,
        BigDecimal thresholdTemp,
        BigDecimal lastTemperature,
        PositionResponse lastPosition,
        Instant lastReportedAt,
        Instant createdAt,
        Instant deliveredAt) {
}
