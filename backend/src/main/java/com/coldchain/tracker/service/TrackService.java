package com.coldchain.tracker.service;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.repository.ReadingRepository;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.dto.GeoJsonLineString;
import com.coldchain.tracker.dto.NamedPositionResponse;
import com.coldchain.tracker.dto.PositionResponse;
import com.coldchain.tracker.dto.TrackResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrackService {

    private static final int MAX_PATH_POINTS = 500;

    private final TrackerRepository trackerRepository;
    private final TrackerLatestRepository trackerLatestRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReadingRepository readingRepository;
    private final JdbcTemplate jdbcTemplate;

    public TrackService(TrackerRepository trackerRepository, TrackerLatestRepository trackerLatestRepository,
            ShipmentRepository shipmentRepository, ReadingRepository readingRepository, JdbcTemplate jdbcTemplate) {
        this.trackerRepository = trackerRepository;
        this.trackerLatestRepository = trackerLatestRepository;
        this.shipmentRepository = shipmentRepository;
        this.readingRepository = readingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public TrackResponse getTrack(String trackerId) {
        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId));

        Shipment shipment = shipmentRepository.findByTrackerIdAndStatusNot(trackerId, ShipmentStatus.DELIVERED)
                .orElseThrow(() -> new ResourceNotFoundException("진행 중인 배송이 없습니다: " + trackerId));

        // 트래커는 배송 완료 후 재사용되므로, 이번 배송 시작 이후의 리딩만 경로에 포함한다.
        List<Reading> newestFirst = readingRepository.findByTrackerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                trackerId, shipment.getCreatedAt(), PageRequest.of(0, MAX_PATH_POINTS));
        List<Reading> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);

        GeoJsonLineString path = GeoJsonLineString.of(chronological.stream()
                .filter(r -> r.getPosition() != null)
                .map(r -> List.of(GeoPoints.lon(r.getPosition()), GeoPoints.lat(r.getPosition())))
                .toList());

        List<GeoJsonLineString> breachSegments = buildBreachSegments(chronological, tracker.getThresholdTemp());

        PositionResponse current = trackerLatestRepository.findById(trackerId)
                .map(latest -> latest.getLastPosition())
                .map(p -> new PositionResponse(GeoPoints.lat(p), GeoPoints.lon(p)))
                .orElse(null);

        NamedPositionResponse destination = new NamedPositionResponse(
                GeoPoints.lat(shipment.getDestinationPosition()),
                GeoPoints.lon(shipment.getDestinationPosition()),
                shipment.getDestinationName());

        Double remainingDistanceMeters = current != null
                ? distanceMeters(current, shipment.getDestinationPosition())
                : null;

        return new TrackResponse(trackerId, path, current, destination, remainingDistanceMeters, breachSegments);
    }

    /**
     * 시간순 리딩을 순회하며 "연속으로 임계 초과"인 구간만 묶어 LineString 목록으로 반환한다.
     * 개별 초과 지점(점)이 아니라 경로상의 초과 구간을 강조하기 위함 — 정상 리딩이 하나라도
     * 끼면 구간이 끊긴다.
     */
    private List<GeoJsonLineString> buildBreachSegments(List<Reading> chronological, BigDecimal thresholdTemp) {
        List<GeoJsonLineString> segments = new ArrayList<>();
        List<List<Double>> currentSegment = new ArrayList<>();

        for (Reading reading : chronological) {
            if (reading.getPosition() == null) {
                continue;
            }
            boolean overThreshold = reading.getTemperature().compareTo(thresholdTemp) > 0;
            if (overThreshold) {
                currentSegment.add(List.of(GeoPoints.lon(reading.getPosition()), GeoPoints.lat(reading.getPosition())));
            } else if (!currentSegment.isEmpty()) {
                segments.add(GeoJsonLineString.of(currentSegment));
                currentSegment = new ArrayList<>();
            }
        }
        if (!currentSegment.isEmpty()) {
            segments.add(GeoJsonLineString.of(currentSegment));
        }

        return segments;
    }

    private Double distanceMeters(PositionResponse current, Point destination) {
        String currentWkt = "POINT(%s %s)".formatted(current.lon(), current.lat());
        String destinationWkt = "POINT(%s %s)".formatted(GeoPoints.lon(destination), GeoPoints.lat(destination));

        return jdbcTemplate.queryForObject(
                "SELECT ST_Distance(ST_GeogFromText(?), ST_GeogFromText(?))",
                Double.class, currentWkt, destinationWkt);
    }
}
