package com.coldchain.detection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldchain.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TemperatureWindowRepositoryTest {

    @Autowired
    private TemperatureWindowRepository windowRepository;

    private String uniqueTrackerId() {
        return "TRK-WINDOW-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void pushAndGetAccumulatesPointsInOrder() {
        String trackerId = uniqueTrackerId();

        windowRepository.pushAndGet(trackerId, new WindowPoint(5.0, 0));
        windowRepository.pushAndGet(trackerId, new WindowPoint(5.5, 60_000));
        List<WindowPoint> window = windowRepository.pushAndGet(trackerId, new WindowPoint(6.0, 120_000));

        assertThat(window).containsExactly(
                new WindowPoint(5.0, 0), new WindowPoint(5.5, 60_000), new WindowPoint(6.0, 120_000));
    }

    @Test
    void outOfOrderPointIsSkipped() {
        String trackerId = uniqueTrackerId();

        windowRepository.pushAndGet(trackerId, new WindowPoint(5.0, 100_000));
        // 이미 push된 것보다 과거 시각 — 스킵되어야 한다
        List<WindowPoint> window = windowRepository.pushAndGet(trackerId, new WindowPoint(99.0, 50_000));

        assertThat(window).containsExactly(new WindowPoint(5.0, 100_000));
    }

    @Test
    void windowIsTrimmedToMaxSize() {
        String trackerId = uniqueTrackerId();
        List<WindowPoint> window = List.of();

        for (int i = 0; i < 25; i++) {
            window = windowRepository.pushAndGet(trackerId, new WindowPoint(5.0 + i * 0.1, i * 60_000L));
        }

        assertThat(window).hasSize(20);
        // 가장 오래된 5개(0~4번째)는 밀려나고 5번째 리딩부터 남아야 한다
        assertThat(window.get(0)).isEqualTo(new WindowPoint(5.5, 5 * 60_000L));
    }
}
