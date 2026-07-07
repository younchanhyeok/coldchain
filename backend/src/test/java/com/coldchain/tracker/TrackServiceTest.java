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
import com.coldchain.tracker.dto.TrackResponse;
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

    @Test
    void pathIsCappedAtFiveHundredMostRecentPoints() {
        String trackerId = "TRK-TRACK-LIMIT";
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
}
