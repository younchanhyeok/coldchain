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

        // 이 shipment의 시간 창 — 트래커는 배송 완료 후 재사용되므로(같은 트래커의 다음 배송 허용),
        // 트래커의 전역 최신 상태(tracker_latest)와 리딩 로그를 이 창으로 제한하지 않으면 매직링크가
        // 다른 배송의 데이터를 노출한다: (a) 완료 후 7일 유효 창 동안 다음 배송의 실시간 위치·온도,
        // (b) 재사용 트래커의 새 배송에서 리딩 도착 전 이전 배송의 잔상(UNKNOWN이어야 할 것이
        // 이전 온도로 SAFE/BREACH 표시).
        Instant windowStart = shipment.getCreatedAt();
        Instant windowEnd = shipment.getDeliveredAt(); // null = 아직 진행 중(상한 없음)

        TrackerLatest latest = trackerLatestRepository.findById(shipment.getTrackerId()).orElse(null);
        boolean latestInWindow = latest != null && latest.getLastTs() != null
                && !latest.getLastTs().isBefore(windowStart)
                && (windowEnd == null || !latest.getLastTs().isAfter(windowEnd));

        List<TemperatureLogPoint> temperatureLog = (windowEnd == null
                ? readingRepository.findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                        shipment.getTrackerId(), windowStart, PageRequest.of(0, MAX_LOG_POINTS))
                : readingRepository.findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        shipment.getTrackerId(), windowStart, windowEnd, PageRequest.of(0, MAX_LOG_POINTS)))
                .stream()
                .sorted(Comparator.comparing(Reading::getRecordedAt))
                .map(r -> new TemperatureLogPoint(r.getRecordedAt(), r.getTemperature()))
                .toList();

        // 창 밖의 latest(다음 배송 진행 중 등)면 이 배송 창 안의 마지막 리딩으로 폴백 — 완료된
        // 배송이 "배송 당시 마지막 온도"를 유지하게 한다(다음 배송 온도로 갱신되지 않도록).
        BigDecimal currentTemperature = latestInWindow
                ? latest.getLastTemp()
                : (temperatureLog.isEmpty() ? null : temperatureLog.get(temperatureLog.size() - 1).temperature());
        // 리딩이 아직 없으면(발급 직후 등) "SAFE"로 단정하지 않는다 — 콜드체인에서 "데이터 없음"을
        // "안전함"으로 오독시키는 건 가장 나쁜 종류의 거짓이다. UNKNOWN으로 정직하게 구분.
        String temperatureStatus;
        if (currentTemperature == null) {
            temperatureStatus = "UNKNOWN";
        } else if (currentTemperature.compareTo(tracker.getThresholdTemp()) > 0) {
            temperatureStatus = "BREACH";
        } else {
            temperatureStatus = "SAFE";
        }
        // 위치는 폴백 없이 창 안의 latest만 — 창 밖이면 실시간 위치가 이미 다음 배송의 것이다.
        PositionResponse position = latestInWindow && latest.getLastPosition() != null
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

        return new ConsigneeTrackResponse(
                new ShipmentSummary(shipment.getProductName(), shipperName, shipment.getStatus(), eta),
                currentTemperature, temperatureStatus, tracker.getThresholdTemp(), position,
                remainingDistanceMeters, temperatureLog);
    }
}
