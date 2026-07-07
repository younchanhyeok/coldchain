package com.coldchain.detection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnomalyDetectionServiceTest {

    @Test
    void zScoreIsZeroWhenBaselineHasNoVariance() {
        List<WindowPoint> baseline = List.of(new WindowPoint(5.0, 0), new WindowPoint(5.0, 60_000));

        assertThat(AnomalyDetectionService.zScore(baseline, 9.0)).isEqualTo(0.0);
    }

    @Test
    void zScoreReflectsDeviationFromBaselineMean() {
        List<WindowPoint> baseline = List.of(
                new WindowPoint(5.0, 0), new WindowPoint(5.2, 60_000),
                new WindowPoint(4.8, 120_000), new WindowPoint(5.1, 180_000));

        double z = AnomalyDetectionService.zScore(baseline, 9.0);

        assertThat(z).isGreaterThan(AnomalyDetectionService.SUDDEN_ZSCORE_THRESHOLD);
    }

    @Test
    void instantRatePerMinuteComputesChangeOverElapsedTime() {
        WindowPoint previous = new WindowPoint(5.0, 0);
        WindowPoint latest = new WindowPoint(6.5, 60_000); // 1분 후 +1.5도

        assertThat(AnomalyDetectionService.instantRatePerMinute(previous, latest)).isEqualTo(1.5, within(0.001));
    }

    @Test
    void instantRatePerMinuteIsZeroWhenTimestampsDoNotAdvance() {
        WindowPoint previous = new WindowPoint(5.0, 1000);
        WindowPoint latest = new WindowPoint(9.0, 1000);

        assertThat(AnomalyDetectionService.instantRatePerMinute(previous, latest)).isEqualTo(0.0);
    }

    @Test
    void slopePerMinuteFitsPerfectLinearTrend() {
        List<WindowPoint> window = List.of(
                new WindowPoint(5.0, 0),
                new WindowPoint(5.3, 60_000),
                new WindowPoint(5.6, 120_000),
                new WindowPoint(5.9, 180_000),
                new WindowPoint(6.2, 240_000));

        assertThat(AnomalyDetectionService.slopePerMinute(window)).isEqualTo(0.3, within(0.01));
    }

    @Test
    void slopePerMinuteIsZeroForSinglePointWindow() {
        assertThat(AnomalyDetectionService.slopePerMinute(List.of(new WindowPoint(5.0, 0)))).isEqualTo(0.0);
    }
}
