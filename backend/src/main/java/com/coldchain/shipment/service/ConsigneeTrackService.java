package com.coldchain.shipment.service;

import com.coldchain.auth.domain.AppUser;
import com.coldchain.auth.domain.MagicLinkToken;
import com.coldchain.auth.repository.AppUserRepository;
import com.coldchain.auth.service.MagicLinkService;
import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.repository.ReadingRepository;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.ConsigneeTrackResponse;
import com.coldchain.shipment.dto.ConsigneeTrackResponse.ShipmentSummary;
import com.coldchain.shipment.dto.ConsigneeTrackResponse.TemperatureLogPoint;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.dto.PositionResponse;
import com.coldchain.tracker.dto.TrackResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매직링크 뷰 조립 — TrackService.getTrack()을 그대로 재사용해(ETA·남은 거리 계산 로직 중복
 * 없음) TrackerOwnershipGuard를 우회한다(그 가드는 컨트롤러 층에만 있어 서비스 재사용엔
 * 안 걸린다, PR2 설계 결정).
 */
@Service
public class ConsigneeTrackService {

    private static final int MAX_LOG_POINTS = 100;

    private final MagicLinkService magicLinkService;
    private final ShipmentRepository shipmentRepository;
    private final AppUserRepository appUserRepository;
    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final ReadingRepository readingRepository;
    private final TrackService trackService;

    public ConsigneeTrackService(MagicLinkService magicLinkService, ShipmentRepository shipmentRepository,
            AppUserRepository appUserRepository, TrackerRepository trackerRepository,
            TrackerLatestRepository trackerLatestRepository, ReadingRepository readingRepository,
            TrackService trackService) {
        this.magicLinkService = magicLinkService;
        this.shipmentRepository = shipmentRepository;
        this.appUserRepository = appUserRepository;
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.readingRepository = readingRepository;
        this.trackService = trackService;
    }

    @Transactional(readOnly = true)
    public ConsigneeTrackResponse getTrack(String token) {
        MagicLinkToken magicLinkToken = magicLinkService.resolve(token);
        Shipment shipment = shipmentRepository.findById(magicLinkToken.getShipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("배송을 찾을 수 없습니다."));
        Tracker tracker = trackerRepository.findById(shipment.getTrackerId())
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다."));
        String shipperName = appUserRepository.findById(shipment.getShipperId())
                .map(AppUser::getCompanyName)
                .orElse(null);

        TrackerLatest latest = trackerLatestRepository.findById(shipment.getTrackerId()).orElse(null);
        BigDecimal currentTemperature = latest != null ? latest.getLastTemp() : null;
        String temperatureStatus = currentTemperature != null && currentTemperature.compareTo(tracker.getThresholdTemp()) > 0
                ? "BREACH"
                : "SAFE";
        PositionResponse position = latest != null && latest.getLastPosition() != null
                ? new PositionResponse(GeoPoints.lat(latest.getLastPosition()), GeoPoints.lon(latest.getLastPosition()))
                : null;

        // IN_TRANSIT일 때만 ETA·남은 거리가 의미 있다 — TrackService.getTrack()도 진행 중
        // shipment가 없으면 404를 던지므로(화주 track과 동일 전제) 그 경우엔 아예 호출하지 않는다.
        Double remainingDistanceMeters = null;
        Instant eta = null;
        if (shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
            TrackResponse track = trackService.getTrack(shipment.getTrackerId());
            remainingDistanceMeters = track.remainingDistanceMeters();
            if (track.etaMinutes() != null) {
                eta = Instant.now().plusSeconds(Math.round(track.etaMinutes() * 60));
            }
        }

        List<TemperatureLogPoint> temperatureLog = readingRepository
                .findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                        shipment.getTrackerId(), shipment.getCreatedAt(), PageRequest.of(0, MAX_LOG_POINTS))
                .stream()
                .sorted(Comparator.comparing(Reading::getRecordedAt))
                .map(r -> new TemperatureLogPoint(r.getRecordedAt(), r.getTemperature()))
                .toList();

        return new ConsigneeTrackResponse(
                new ShipmentSummary(shipment.getProductName(), shipperName, shipment.getStatus(), eta),
                currentTemperature, temperatureStatus, tracker.getThresholdTemp(), position,
                remainingDistanceMeters, temperatureLog);
    }
}
