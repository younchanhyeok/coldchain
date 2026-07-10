package com.coldchain.shipment.dto;

import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.tracker.dto.PositionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/track/{token} — 수령기관 매직링크 뷰(명세 §6). 노출 범위를 의도적으로 최소화한다:
 * 화주 내부 통계·다른 배송·트래커ID 원문을 이 응답이 아예 필드로 갖지 않는다(직렬화 누락이
 * 아니라 타입으로 비노출을 보장) — TrackResponse(화주용)를 그대로 재사용하지 않는 이유.
 */
public record ConsigneeTrackResponse(
        ShipmentSummary shipment,
        BigDecimal currentTemperature,
        String temperatureStatus,
        BigDecimal thresholdTemp,
        PositionResponse position,
        Double remainingDistanceMeters,
        List<TemperatureLogPoint> temperatureLog) {

    public record ShipmentSummary(String productName, String shipperName, ShipmentStatus status, Instant eta) {
    }

    public record TemperatureLogPoint(Instant ts, BigDecimal temperature) {
    }
}
