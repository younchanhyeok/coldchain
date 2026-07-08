package com.coldchain.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.common.GeoPoints;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.repository.ReadingRepository;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.domain.TrackerLatest;
import com.coldchain.tracker.dto.TrackResponse;
import com.coldchain.tracker.repository.TrackerLatestRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TrackServiceTest {

    @Autowired
    private TrackService trackService;

    @Autowired
    private TrackerRepository trackerRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private TrackerLatestRepository trackerLatestRepository;

    private Shipment givenTrackerWithActiveShipment(String trackerId) {
        trackerRepository.save(new Tracker(trackerId, 1L, "백신 A", new BigDecimal("8.0"), "hash"));

        Shipment shipment = new Shipment(
                1L, trackerId, "백신 A",
                GeoPoints.of(37.42, 127.12), "성남 물류센터",
                GeoPoints.of(37.58, 127.00), "서울대병원 약제부",
                "서울대병원 약제부", "02-000-0000", "010-0000-0000");
        shipmentRepository.save(shipment);
        shipmentRepository.flush();
        shipment.transitionTo(ShipmentStatus.IN_TRANSIT);
        shipmentRepository.save(shipment);
        return shipment;
    }

    @Test
    void pathIsCappedAtFiveHundredMostRecentPoints() {
        String trackerId = "TRK-TRACK-LIMIT";
        Shipment shipment = givenTrackerWithActiveShipment(trackerId);

        // 리딩은 반드시 배송 생성 시각 이후여야 한다 — TrackService가 이번 배송 기간으로 필터링한다.
        Instant base = shipment.getCreatedAt().plusSeconds(1);

        List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            readings.add(new Reading(trackerId, base.plusSeconds(i), new BigDecimal("5.0"),
                    GeoPoints.of(37.42 + i * 0.0001, 127.12)));
        }
        readingRepository.saveAll(readings);

        TrackResponse track = trackService.getTrack(trackerId);

        assertThat(track.path().coordinates()).hasSize(500);
        // 가장 최근 500개만 남아야 하므로 마지막 좌표는 599번째(가장 최신) 리딩과 일치해야 한다
        assertThat(track.path().coordinates().get(499).get(1)).isEqualTo(37.42 + 599 * 0.0001);
        // 가장 오래된 리딩(i=0)은 500개 제한에 밀려 포함되지 않는다
        assertThat(track.path().coordinates().get(0).get(1)).isEqualTo(37.42 + 100 * 0.0001);
    }

    @Test
    void groupsConsecutiveOverThresholdReadingsIntoSeparateSegments() {
        String trackerId = "TRK-TRACK-SEGMENTS";
        Shipment shipment = givenTrackerWithActiveShipment(trackerId);
        Instant base = shipment.getCreatedAt().plusSeconds(1);

        // 정상 → 초과(구간1 시작) → 초과(구간1 지속) → 정상 → 초과(구간2)
        readingRepository.saveAll(List.of(
                new Reading(trackerId, base, new BigDecimal("5.0"), GeoPoints.of(37.420, 127.12)),
                new Reading(trackerId, base.plusSeconds(1), new BigDecimal("9.0"), GeoPoints.of(37.421, 127.12)),
                new Reading(trackerId, base.plusSeconds(2), new BigDecimal("9.5"), GeoPoints.of(37.422, 127.12)),
                new Reading(trackerId, base.plusSeconds(3), new BigDecimal("5.0"), GeoPoints.of(37.423, 127.12)),
                new Reading(trackerId, base.plusSeconds(4), new BigDecimal("10.0"), GeoPoints.of(37.424, 127.12))));

        TrackResponse track = trackService.getTrack(trackerId);

        assertThat(track.breachSegments()).hasSize(2);
        assertThat(track.breachSegments().get(0).coordinates()).hasSize(2); // 초과 2연속
        assertThat(track.breachSegments().get(1).coordinates()).hasSize(1); // 초과 1건, 정상으로 끊김
        assertThat(track.breachSegments().get(0).coordinates().get(0).get(1)).isEqualTo(37.421);
        assertThat(track.breachSegments().get(1).coordinates().get(0).get(1)).isEqualTo(37.424);
    }

    @Test
    void etaMinutesIsCalculatedFromRecentMovementSpeed() {
        String trackerId = "TRK-TRACK-ETA-MOVING";
        Shipment shipment = givenTrackerWithActiveShipment(trackerId);
        Instant base = shipment.getCreatedAt().plusSeconds(1);

        // 60초 간격으로 위도 0.0001도(약 11m)씩 북상 — 정지 판정 임계(0.1 m/s)를 넉넉히 넘는 속도
        List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            readings.add(new Reading(trackerId, base.plusSeconds(i * 60L), new BigDecimal("5.0"),
                    GeoPoints.of(37.42 + i * 0.0001, 127.12)));
        }
        readingRepository.saveAll(readings);

        TrackerLatest latest = new TrackerLatest(trackerId);
        latest.applyReading(base.plusSeconds(240), new BigDecimal("5.0"), GeoPoints.of(37.4204, 127.12));
        trackerLatestRepository.save(latest);

        TrackResponse track = trackService.getTrack(trackerId);

        assertThat(track.etaMinutes()).isNotNull();
        assertThat(track.etaMinutes()).isGreaterThan(0);
    }

    @Test
    void etaMinutesIsNullWhenTrackerIsStationary() {
        String trackerId = "TRK-TRACK-ETA-STATIONARY";
        Shipment shipment = givenTrackerWithActiveShipment(trackerId);
        Instant base = shipment.getCreatedAt().plusSeconds(1);

        // 같은 위치에 계속 머무름 — 이동거리 0
        List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            readings.add(new Reading(trackerId, base.plusSeconds(i * 60L), new BigDecimal("5.0"),
                    GeoPoints.of(37.42, 127.12)));
        }
        readingRepository.saveAll(readings);

        TrackerLatest latest = new TrackerLatest(trackerId);
        latest.applyReading(base.plusSeconds(240), new BigDecimal("5.0"), GeoPoints.of(37.42, 127.12));
        trackerLatestRepository.save(latest);

        TrackResponse track = trackService.getTrack(trackerId);

        assertThat(track.etaMinutes()).isNull();
    }
}
