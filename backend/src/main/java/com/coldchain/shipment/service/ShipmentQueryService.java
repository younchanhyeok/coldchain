package com.coldchain.shipment.service;

import com.coldchain.auth.AuthenticatedUserProvider;
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
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final TrackerQueryService trackerQueryService;

    public ShipmentQueryService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, AuthenticatedUserProvider authenticatedUserProvider,
            TrackerQueryService trackerQueryService) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.trackerQueryService = trackerQueryService;
    }

    public ShipmentListResponse list(int page, int size) {
        Page<Shipment> result = shipmentRepository.findByShipperIdOrderByCreatedAtDesc(
                authenticatedUserProvider.shipperId(), PageRequest.of(page, size));

        // PERF(M6): N+1 — shipment마다 tracker/latest를 findById로 건당 조회한다(findAllById로
        // 배치 가능). 지금은 정합성엔 문제없는 순수 성능 이슈라 M6 부하테스트에서 실측 후 고친다.
        return new ShipmentListResponse(
                result.getContent().stream().map(this::toSummary).toList(),
                page, size, result.getTotalElements());
    }

    private ShipmentSummaryResponse toSummary(Shipment shipment) {
        Tracker tracker = trackerRepository.findById(shipment.getTrackerId()).orElse(null);
        TrackerLatest latest = tracker != null
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
                shipment.getInTransitAt(),
                shipment.getDeliveredAt());
    }
}
