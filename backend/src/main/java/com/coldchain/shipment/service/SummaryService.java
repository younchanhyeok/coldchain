package com.coldchain.shipment.service;

import com.coldchain.auth.AuthenticatedUserProvider;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.SummaryResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

    private final ShipmentRepository shipmentRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final PredictionRepository predictionRepository;

    public SummaryService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, AuthenticatedUserProvider authenticatedUserProvider,
            PredictionRepository predictionRepository) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.predictionRepository = predictionRepository;
    }

    public SummaryResponse getSummary() {
        List<Shipment> shipments = shipmentRepository.findByShipperId(authenticatedUserProvider.shipperId());

        int totalShipments = shipments.size();
        int inTransit = 0;
        int deliveredCount = 0;
        int breachCount = 0;

        // IN_TRANSIT shipment마다 tracker/latest를 건당 조회하던 N+1을 IN 배치 2쿼리로(M6) —
        // 부하테스트에서 /summary가 5000 트래커 기준 p50 9.7초까지 밀렸던 원인.
        List<String> inTransitTrackerIds = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.IN_TRANSIT)
                .map(Shipment::getTrackerId)
                .distinct()
                .toList();
        Map<String, Tracker> trackers = new HashMap<>();
        trackerRepository.findAllById(inTransitTrackerIds).forEach(t -> trackers.put(t.getId(), t));
        Map<String, TrackerLatest> latests = new HashMap<>();
        trackerLatestRepository.findAllById(inTransitTrackerIds).forEach(l -> latests.put(l.getTrackerId(), l));

        for (Shipment shipment : shipments) {
            if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
                inTransit++;
                if (isBreached(trackers.get(shipment.getTrackerId()), latests.get(shipment.getTrackerId()))) {
                    breachCount++;
                }
            } else if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
                deliveredCount++;
            }
        }

        Integer avgDeliveryMinutes = averageDeliveryMinutes(shipments);

        int rescuedByPrediction = (int) predictionRepository.countByStatusIn(
                List.of(PredictionStatus.CANCELED, PredictionStatus.EXPIRED));

        return new SummaryResponse(
                totalShipments, inTransit, breachCount, deliveredCount, rescuedByPrediction, avgDeliveryMinutes);
    }

    // BREACH 여부만 필요하므로 CAUTION 판정(활성 이상탐지 조회까지 포함하는
    // TrackerQueryService의 상태 판정)을 통째로 타지 않고 온도 비교로 짧게 끊는다 —
    // 안 그러면 이탈 아닌 진행 중 화물마다 불필요한 anomaly 조회가 한 번씩 더 나간다.
    private boolean isBreached(Tracker tracker, TrackerLatest latest) {
        if (tracker == null || latest == null || latest.getLastTemp() == null) {
            return false;
        }
        return latest.getLastTemp().compareTo(tracker.getThresholdTemp()) > 0;
    }

    /** "배송 소요시간"이므로 주문 생성(createdAt)이 아니라 출발(IN_TRANSIT 전이) 기준으로 잰다. */
    private Integer averageDeliveryMinutes(List<Shipment> shipments) {
        OptionalDouble average = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED
                        && s.getInTransitAt() != null && s.getDeliveredAt() != null)
                .mapToLong(s -> Duration.between(s.getInTransitAt(), s.getDeliveredAt()).toMinutes())
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }
}
