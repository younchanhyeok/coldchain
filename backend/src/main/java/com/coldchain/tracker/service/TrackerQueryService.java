package com.coldchain.tracker.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.detection.service.AnomalyQueryService;
import com.coldchain.prediction.dto.ActivePredictionSummary;
import com.coldchain.prediction.service.PredictionQueryService;
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
    private final PredictionQueryService predictionQueryService;

    public TrackerQueryService(TrackerRepository trackerRepository, TrackerLatestRepository trackerLatestRepository,
            ShipmentRepository shipmentRepository, DevShipperProvider devShipperProvider,
            AnomalyQueryService anomalyQueryService, PredictionQueryService predictionQueryService) {
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.shipmentRepository = shipmentRepository;
        this.devShipperProvider = devShipperProvider;
        this.predictionQueryService = predictionQueryService;
        this.anomalyQueryService = anomalyQueryService;
    }

    public TrackerListResponse list(TrackerStatus statusFilter, ShipmentStatus shipmentStatus, int page, int size) {
        List<Shipment> shipments = shipmentRepository.findByShipperIdAndStatus(
                devShipperProvider.shipperId(), shipmentStatus);

        // PERF(M6): N+1 — shipment마다 tracker/latest를 findById로 건당 조회한다(findAllById로
        // 배치 가능). 필터·정렬·페이지네이션도 전건을 메모리에 올린 뒤 자바에서 수행 중이라
        // M6에서 배치 조회와 함께 DB 사이드 페이지네이션으로 내려야 한다. 지금은 정합성엔
        // 문제없는 순수 성능 이슈라 M6 부하테스트에서 실측 후 고친다.
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

        return new TrackerDetailResponse(summary, shipmentSummary, anomalyQueryService.findActiveAnomalies(trackerId));
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

        // activePrediction과 RISK 판정은 같은 쿼리 결과를 공유한다 — 따로따로 부르면
        // (hasActivePrediction + findActiveSummary) 트래커 하나당 같은 조회가 중복된다.
        ActivePredictionSummary activePrediction = predictionQueryService.findActiveSummary(tracker.getId())
                .orElse(null);
        TrackerStatus status = computeStatus(tracker, latest, activePrediction != null);

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
                activePrediction);
    }

    /** shipment 목록(ShipmentQueryService) 등 다른 서비스에서도 같은 판정 로직을 재사용한다. */
    public TrackerStatus computeStatus(Tracker tracker, TrackerLatest latest) {
        return computeStatus(tracker, latest, predictionQueryService.hasActivePrediction(tracker.getId()));
    }

    private TrackerStatus computeStatus(Tracker tracker, TrackerLatest latest, boolean hasActivePrediction) {
        if (latest == null || latest.getLastTemp() == null) {
            return TrackerStatus.SAFE;
        }
        if (latest.getLastTemp().compareTo(tracker.getThresholdTemp()) > 0) {
            return TrackerStatus.BREACH;
        }
        // RISK: L3가 "N분 후 이탈"을 실제로 예측한 상태 — CAUTION(이상 감지, 방향성 미확정)보다
        // 구체적이고 심각한 신호라 먼저 검사한다.
        if (hasActivePrediction) {
            return TrackerStatus.RISK;
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
        return new ShipmentSummary(
                origin, destination, shipment.getConsigneeName(), shipment.getDriverContact(), shipment.getStatus());
    }
}
