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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // shipment마다 tracker/latest/prediction/anomaly를 건당 조회하던 N+1을 IN 배치로(M6).
        // 페이지 크기만큼이라 트래커 목록보다는 덜했지만 같은 패턴은 같은 방식으로 없앤다.
        List<String> trackerIds = result.getContent().stream().map(Shipment::getTrackerId).distinct().toList();
        Map<String, Tracker> trackers = new HashMap<>();
        trackerRepository.findAllById(trackerIds).forEach(t -> trackers.put(t.getId(), t));
        Map<String, TrackerLatest> latests = new HashMap<>();
        trackerLatestRepository.findAllById(trackerIds).forEach(l -> latests.put(l.getTrackerId(), l));
        Map<String, TrackerStatus> statuses = trackerQueryService.computeStatusBatch(trackers.values(), latests);

        return new ShipmentListResponse(
                result.getContent().stream()
                        .map(shipment -> toSummary(shipment,
                                trackers.get(shipment.getTrackerId()),
                                latests.get(shipment.getTrackerId()),
                                statuses.get(shipment.getTrackerId())))
                        .toList(),
                page, size, result.getTotalElements());
    }

    private ShipmentSummaryResponse toSummary(Shipment shipment, Tracker tracker, TrackerLatest latest,
            TrackerStatus trackerStatus) {
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
