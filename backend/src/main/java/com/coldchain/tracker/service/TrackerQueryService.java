package com.coldchain.tracker.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.detection.service.AnomalyQueryService;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.domain.TrackerStatus;
import com.coldchain.tracker.dto.PositionResponse;
import com.coldchain.tracker.dto.ShipmentSummary;
import com.coldchain.tracker.dto.TrackerDetailResponse;
import com.coldchain.tracker.dto.TrackerListResponse;
import com.coldchain.tracker.dto.TrackerSummaryResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TrackerQueryService {

    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final ShipmentRepository shipmentRepository;
    private final DevShipperProvider devShipperProvider;
    private final AnomalyQueryService anomalyQueryService;

    public TrackerQueryService(TrackerRepository trackerRepository, TrackerLatestRepository trackerLatestRepository,
            ShipmentRepository shipmentRepository, DevShipperProvider devShipperProvider,
            AnomalyQueryService anomalyQueryService) {
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.shipmentRepository = shipmentRepository;
        this.devShipperProvider = devShipperProvider;
        this.anomalyQueryService = anomalyQueryService;
    }

    public TrackerListResponse list(TrackerStatus statusFilter, ShipmentStatus shipmentStatus, int page, int size) {
        List<Shipment> shipments = shipmentRepository.findByShipperIdAndStatus(
                devShipperProvider.shipperId(), shipmentStatus);

        // PERF(M6): N+1 — shipment마다 tracker/latest를 findById로 건당 조회한다(findAllById로
        // 배치 가능). 지금은 정합성엔 문제없는 순수 성능 이슈라 M6 부하테스트에서 실측 후 고친다.
        List<TrackerSummaryResponse> summaries = shipments.stream()
                .map(shipment -> toSummary(shipment, findTracker(shipment.getTrackerId())))
                .filter(summary -> statusFilter == null || summary.status() == statusFilter)
                .sorted(Comparator.comparing(TrackerSummaryResponse::lastReportedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(page * size, summaries.size());
        int toIndex = Math.min(fromIndex + size, summaries.size());

        return new TrackerListResponse(summaries.subList(fromIndex, toIndex), page, size, summaries.size());
    }

    public TrackerDetailResponse detail(String trackerId) {
        Tracker tracker = findTracker(trackerId);
        Optional<Shipment> activeShipment = shipmentRepository.findByTrackerIdAndStatusNot(
                trackerId, ShipmentStatus.DELIVERED);

        TrackerSummaryResponse summary = toSummary(activeShipment.orElse(null), tracker);
        ShipmentSummary shipmentSummary = activeShipment.map(TrackerQueryService::toShipmentSummary).orElse(null);

        return new TrackerDetailResponse(summary, shipmentSummary, List.of());
    }

    private Tracker findTracker(String trackerId) {
        return trackerRepository.findById(trackerId)
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId));
    }

    private TrackerSummaryResponse toSummary(Shipment shipment, Tracker tracker) {
        TrackerLatest latest = trackerLatestRepository.findById(tracker.getId()).orElse(null);

        PositionResponse lastPosition = latest != null && latest.getLastPosition() != null
                ? new PositionResponse(GeoPoints.lat(latest.getLastPosition()), GeoPoints.lon(latest.getLastPosition()))
                : null;

        TrackerStatus status = computeStatus(tracker, latest);

        return new TrackerSummaryResponse(
                tracker.getId(),
                shipment != null ? shipment.getId() : null,
                tracker.getProductName(),
                shipment != null ? shipment.getOriginName() : null,
                shipment != null ? shipment.getDestinationName() : null,
                tracker.getThresholdTemp(),
                status,
                latest != null ? latest.getLastTemp() : null,
                lastPosition,
                latest != null ? latest.getLastTs() : null,
                null);
    }

    /** shipment 목록(ShipmentQueryService) 등 다른 서비스에서도 같은 판정 로직을 재사용한다. */
    public TrackerStatus computeStatus(Tracker tracker, TrackerLatest latest) {
        if (latest == null || latest.getLastTemp() == null) {
            return TrackerStatus.SAFE;
        }
        if (latest.getLastTemp().compareTo(tracker.getThresholdTemp()) > 0) {
            return TrackerStatus.BREACH;
        }
        // CAUTION: 유형(SUDDEN/GRADUAL) 무관하게 활성 이상탐지가 있으면 "이탈은 아니지만 이상 감지됨"
        if (anomalyQueryService.hasActiveAnomaly(tracker.getId())) {
            return TrackerStatus.CAUTION;
        }
        return TrackerStatus.SAFE;
    }

    private static ShipmentSummary toShipmentSummary(Shipment shipment) {
        PositionResponse origin = shipment.getOriginPosition() != null
                ? new PositionResponse(GeoPoints.lat(shipment.getOriginPosition()), GeoPoints.lon(shipment.getOriginPosition()))
                : null;
        PositionResponse destination = shipment.getDestinationPosition() != null
                ? new PositionResponse(GeoPoints.lat(shipment.getDestinationPosition()), GeoPoints.lon(shipment.getDestinationPosition()))
                : null;
        return new ShipmentSummary(origin, destination, shipment.getConsigneeName(), shipment.getStatus());
    }
}
