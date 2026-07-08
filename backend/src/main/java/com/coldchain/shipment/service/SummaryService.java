package com.coldchain.shipment.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.SummaryResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

    private final ShipmentRepository shipmentRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final DevShipperProvider devShipperProvider;

    public SummaryService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, DevShipperProvider devShipperProvider) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.devShipperProvider = devShipperProvider;
    }

    public SummaryResponse getSummary() {
        List<Shipment> shipments = shipmentRepository.findByShipperId(devShipperProvider.shipperId());

        // 진행 중인 배송의 트래커/최신상태만 필요 — 건당 findById 대신 한 번에 모아 조회한다
        // (N건이어도 쿼리 2번, M6 이전엔 이 정도로 충분).
        List<String> inTransitTrackerIds = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.IN_TRANSIT)
                .map(Shipment::getTrackerId)
                .distinct()
                .toList();
        Map<String, Tracker> trackersById = trackerRepository.findAllById(inTransitTrackerIds).stream()
                .collect(Collectors.toMap(Tracker::getId, Function.identity()));
        Map<String, TrackerLatest> latestById = trackerLatestRepository.findAllById(inTransitTrackerIds).stream()
                .collect(Collectors.toMap(TrackerLatest::getTrackerId, Function.identity()));

        int totalShipments = shipments.size();
        int inTransit = 0;
        int deliveredCount = 0;
        int breachCount = 0;

        for (Shipment shipment : shipments) {
            if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
                inTransit++;
                if (isBreached(trackersById.get(shipment.getTrackerId()), latestById.get(shipment.getTrackerId()))) {
                    breachCount++;
                }
            } else if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
                deliveredCount++;
            }
        }

        Integer avgDeliveryMinutes = averageDeliveryMinutes(shipments);

        // M3엔 예측이 없어 0(생략이 아니라 정직한 0) — M4에서 값만 배선 교체
        return new SummaryResponse(totalShipments, inTransit, breachCount, deliveredCount, 0, avgDeliveryMinutes);
    }

    // BREACH 여부만 필요하므로 CAUTION 판정(활성 이상탐지 조회까지 포함하는
    // TrackerQueryService.computeStatus)을 통째로 타지 않고 온도 비교로 짧게 끊는다 —
    // 안 그러면 이탈 아닌 진행 중 화물마다 불필요한 anomaly exists 쿼리가 한 번씩 더 나간다.
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
