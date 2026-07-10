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
import java.util.List;
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

        // PERF(M6): N+1 — IN_TRANSIT shipment마다 tracker/latest를 findById로 건당 조회한다
        // (findAllById로 배치 가능). 지금은 정합성엔 문제없는 순수 성능 이슈라 M6 부하테스트에서
        // 실측 후 고친다.
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

        int rescuedByPrediction = (int) predictionRepository.countByStatusIn(
                List.of(PredictionStatus.CANCELED, PredictionStatus.EXPIRED));

        return new SummaryResponse(
                totalShipments, inTransit, breachCount, deliveredCount, rescuedByPrediction, avgDeliveryMinutes);
    }

    // BREACH 여부만 필요하므로 CAUTION 판정(활성 이상탐지 조회까지 포함하는
    // TrackerQueryService.computeStatus)을 통째로 타지 않고 온도 비교로 짧게 끊는다 —
    // 안 그러면 이탈 아닌 진행 중 화물마다 불필요한 anomaly exists 쿼리가 한 번씩 더 나간다.
    // (이건 N+1 배치 여부와 무관한 별개 최적화 — 요청 수가 아니라 요청당 작업량 문제.)
    private boolean isBreached(String trackerId) {
        Tracker tracker = trackerRepository.findById(trackerId).orElse(null);
        if (tracker == null) {
            return false;
        }
        TrackerLatest latest = trackerLatestRepository.findById(trackerId).orElse(null);
        if (latest == null || latest.getLastTemp() == null) {
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
