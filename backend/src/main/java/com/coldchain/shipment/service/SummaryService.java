package com.coldchain.shipment.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.SummaryResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.domain.TrackerStatus;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerQueryService;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

    private final ShipmentRepository shipmentRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final DevShipperProvider devShipperProvider;
    private final TrackerQueryService trackerQueryService;

    public SummaryService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, DevShipperProvider devShipperProvider,
            TrackerQueryService trackerQueryService) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.devShipperProvider = devShipperProvider;
        this.trackerQueryService = trackerQueryService;
    }

    public SummaryResponse getSummary() {
        List<Shipment> shipments = shipmentRepository.findByShipperId(devShipperProvider.shipperId());

        int totalShipments = shipments.size();
        int inTransit = 0;
        int deliveredCount = 0;
        int breachCount = 0;

        for (Shipment shipment : shipments) {
            if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
                inTransit++;
                if (isBreached(shipment.getTrackerId())) {
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

    private boolean isBreached(String trackerId) {
        Tracker tracker = trackerRepository.findById(trackerId).orElse(null);
        if (tracker == null) {
            return false;
        }
        TrackerLatest latest = trackerLatestRepository.findById(trackerId).orElse(null);
        return trackerQueryService.computeStatus(tracker, latest) == TrackerStatus.BREACH;
    }

    private Integer averageDeliveryMinutes(List<Shipment> shipments) {
        OptionalDouble average = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED && s.getDeliveredAt() != null)
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getDeliveredAt()).toMinutes())
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }
}
