package com.coldchain.tracker.service;

import com.coldchain.auth.AuthenticatedUserProvider;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class TrackerQueryService {

    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final ShipmentRepository shipmentRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AnomalyQueryService anomalyQueryService;
    private final PredictionQueryService predictionQueryService;

    public TrackerQueryService(TrackerRepository trackerRepository, TrackerLatestRepository trackerLatestRepository,
            ShipmentRepository shipmentRepository, AuthenticatedUserProvider authenticatedUserProvider,
            AnomalyQueryService anomalyQueryService, PredictionQueryService predictionQueryService) {
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.shipmentRepository = shipmentRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.predictionQueryService = predictionQueryService;
        this.anomalyQueryService = anomalyQueryService;
    }

    public TrackerListResponse list(TrackerStatus statusFilter, ShipmentStatus shipmentStatus, int page, int size) {
        List<Shipment> shipments = shipmentRepository.findByShipperIdAndStatus(
                authenticatedUserProvider.shipperId(), shipmentStatus);

        // 트래커별 건당 조회 4종(tracker/latest/prediction/anomaly)을 IN 배치 5쿼리로 —
        // M6 부하테스트에서 이 N+1이 5000 트래커 기준 p50 22.7초(30s 타임아웃 83%)의 주범.
        // 필터·정렬·페이지네이션은 여전히 메모리에서 한다: 필터 기준(status)이 계산값이고 정렬
        // 기준(lastTs)이 조인 대상이라 DB로 내리려면 큰 네이티브 쿼리가 필요한데, 병목 실측 결과
        // 비용은 왕복 횟수였지 메모리 조립이 아니었다(수천 건 정렬은 ms 단위).
        List<String> trackerIds = shipments.stream().map(Shipment::getTrackerId).distinct().toList();
        Map<String, Tracker> trackers = byId(trackerRepository.findAllById(trackerIds), Tracker::getId);
        Map<String, TrackerLatest> latests = byId(trackerLatestRepository.findAllById(trackerIds),
                TrackerLatest::getTrackerId);
        Map<String, ActivePredictionSummary> activePredictions =
                predictionQueryService.findActiveSummaries(trackerIds);
        Set<String> anomalous = anomalyQueryService.trackerIdsWithActiveAnomaly(trackerIds);

        List<TrackerSummaryResponse> summaries = shipments.stream()
                .filter(shipment -> trackers.containsKey(shipment.getTrackerId())) // FK가 보장 — 방어적 스킵
                .map(shipment -> toSummary(shipment, trackers.get(shipment.getTrackerId()),
                        latests.get(shipment.getTrackerId()),
                        activePredictions.get(shipment.getTrackerId()),
                        anomalous.contains(shipment.getTrackerId())))
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

        // activePrediction과 RISK 판정은 같은 쿼리 결과를 공유한다 — 따로따로 부르면
        // (hasActivePrediction + findActiveSummary) 같은 조회가 중복된다.
        TrackerSummaryResponse summary = toSummary(activeShipment.orElse(null), tracker,
                trackerLatestRepository.findById(trackerId).orElse(null),
                predictionQueryService.findActiveSummary(trackerId).orElse(null),
                anomalyQueryService.hasActiveAnomaly(trackerId));
        ShipmentSummary shipmentSummary = activeShipment.map(TrackerQueryService::toShipmentSummary).orElse(null);

        return new TrackerDetailResponse(summary, shipmentSummary, anomalyQueryService.findActiveAnomalies(trackerId));
    }

    private Tracker findTracker(String trackerId) {
        return trackerRepository.findById(trackerId)
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId));
    }

    private static <T> Map<String, T> byId(Iterable<T> entities, Function<T, String> idOf) {
        Map<String, T> map = new HashMap<>();
        entities.forEach(e -> map.put(idOf.apply(e), e));
        return map;
    }

    private TrackerSummaryResponse toSummary(Shipment shipment, Tracker tracker, TrackerLatest latest,
            ActivePredictionSummary activePrediction, boolean hasActiveAnomaly) {
        PositionResponse lastPosition = latest != null && latest.getLastPosition() != null
                ? new PositionResponse(GeoPoints.lat(latest.getLastPosition()), GeoPoints.lon(latest.getLastPosition()))
                : null;

        TrackerStatus status = computeStatus(tracker, latest, activePrediction != null, hasActiveAnomaly);

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

    /** shipment 목록(ShipmentQueryService) 등 다른 서비스가 쓰는 배치 판정 — 트래커마다
     *  prediction/anomaly를 건당 조회하면 N+1이라(M6 실측) 집합 2쿼리로 끝낸다. */
    public Map<String, TrackerStatus> computeStatusBatch(Collection<Tracker> trackers,
            Map<String, TrackerLatest> latestById) {
        List<String> ids = trackers.stream().map(Tracker::getId).toList();
        Set<String> predicted = predictionQueryService.findActiveSummaries(ids).keySet();
        Set<String> anomalous = anomalyQueryService.trackerIdsWithActiveAnomaly(ids);

        Map<String, TrackerStatus> statuses = new HashMap<>();
        for (Tracker tracker : trackers) {
            statuses.put(tracker.getId(), computeStatus(tracker, latestById.get(tracker.getId()),
                    predicted.contains(tracker.getId()), anomalous.contains(tracker.getId())));
        }
        return statuses;
    }

    private TrackerStatus computeStatus(Tracker tracker, TrackerLatest latest, boolean hasActivePrediction,
            boolean hasActiveAnomaly) {
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
        if (hasActiveAnomaly) {
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
