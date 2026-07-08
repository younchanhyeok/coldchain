package com.coldchain.shipment.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.common.GeoPoints;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.dto.ShipmentListResponse;
import com.coldchain.shipment.dto.ShipmentSummaryResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.domain.TrackerStatus;
import com.coldchain.tracker.dto.PositionResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 화물 관리 탭 좌측 리스트 — shipment 기준(트래커 재사용 때문에 완료 건도 shipment로만 표현 가능). */
@Service
public class ShipmentQueryService {

    private final ShipmentRepository shipmentRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final DevShipperProvider devShipperProvider;
    private final TrackerQueryService trackerQueryService;

    public ShipmentQueryService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, DevShipperProvider devShipperProvider,
            TrackerQueryService trackerQueryService) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.devShipperProvider = devShipperProvider;
        this.trackerQueryService = trackerQueryService;
    }

    public ShipmentListResponse list(int page, int size) {
        Page<Shipment> result = shipmentRepository.findByShipperIdOrderByCreatedAtDesc(
                devShipperProvider.shipperId(), PageRequest.of(page, size));

        return new ShipmentListResponse(
                result.getContent().stream().map(this::toSummary).toList(),
                page, size, result.getTotalElements());
    }

    private ShipmentSummaryResponse toSummary(Shipment shipment) {
        Tracker tracker = trackerRepository.findById(shipment.getTrackerId()).orElse(null);
        TrackerLatest latest = trackerRepository.existsById(shipment.getTrackerId())
                ? trackerLatestRepository.findById(shipment.getTrackerId()).orElse(null)
                : null;

        TrackerStatus trackerStatus = tracker != null ? trackerQueryService.computeStatus(tracker, latest) : null;
        PositionResponse lastPosition = latest != null && latest.getLastPosition() != null
                ? new PositionResponse(GeoPoints.lat(latest.getLastPosition()), GeoPoints.lon(latest.getLastPosition()))
                : null;

        return new ShipmentSummaryResponse(
                shipment.getId(),
                shipment.getTrackerId(),
                shipment.getProductName(),
                shipment.getOriginName(),
                shipment.getDestinationName(),
                shipment.getStatus(),
                trackerStatus,
                tracker != null ? tracker.getThresholdTemp() : null,
                latest != null ? latest.getLastTemp() : null,
                lastPosition,
                latest != null ? latest.getLastTs() : null,
                shipment.getCreatedAt(),
                shipment.getDeliveredAt());
    }
}
