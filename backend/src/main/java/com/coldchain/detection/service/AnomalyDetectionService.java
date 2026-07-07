package com.coldchain.detection.service;

import com.coldchain.detection.domain.AnomalyEvent;
import com.coldchain.detection.domain.AnomalySeverity;
import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.domain.AnomalyType;
import com.coldchain.detection.event.AnomalyDetectedEvent;
import com.coldchain.detection.repository.AnomalyEventRepository;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L2 이상탐지 — z-score(급변)·최소자승 기울기(점진 이탈)로 SUDDEN/GRADUAL을 판정한다.
 * 같은 (tracker, type) 활성 이상은 최대 1건 — 계속 감지돼도 새로 저장/발행하지 않고
 * 조건이 CLEAR_STREAK_THRESHOLD회 연속 미해당이면 해제(CLEARED)한다.
 */
@Service
public class AnomalyDetectionService {

    static final int MIN_WINDOW_SIZE = 5;
    static final int CLEAR_STREAK_THRESHOLD = 3;
    static final double SUDDEN_RATE_PER_MINUTE_THRESHOLD = 1.5;
    static final double SUDDEN_ZSCORE_THRESHOLD = 3.0;
    static final double GRADUAL_SLOPE_PER_MINUTE_THRESHOLD = 0.3;

    private final TemperatureWindowRepository windowRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AnomalyDetectionService(TemperatureWindowRepository windowRepository,
            AnomalyEventRepository anomalyEventRepository, ApplicationEventPublisher eventPublisher) {
        this.windowRepository = windowRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void analyze(ReadingRecordedEvent event) {
        WindowPoint point = new WindowPoint(event.temperature().doubleValue(), event.recordedAt().toEpochMilli());
        List<WindowPoint> window = windowRepository.pushAndGet(event.trackerId(), point);

        if (window.size() < MIN_WINDOW_SIZE) {
            return; // 콜드 스타트 가드 — 표준편차 0/불안정한 판정 방지
        }

        List<WindowPoint> baseline = window.subList(0, window.size() - 1);
        double zScore = zScore(baseline, point.temperature());
        double instantRate = instantRatePerMinute(window.get(window.size() - 2), point);
        double slope = slopePerMinute(window);

        boolean suddenTriggered = Math.abs(instantRate) > SUDDEN_RATE_PER_MINUTE_THRESHOLD
                || Math.abs(zScore) > SUDDEN_ZSCORE_THRESHOLD;
        // GRADUAL은 상승 추세(임계 접근)만 본다 — 이 프로젝트의 breach는 상한 초과뿐이라 하강 추세는
        // 리스크가 아니다. SUDDEN은 급변 자체가 이상이므로 방향 불문(abs)으로 잡는다.
        boolean gradualTriggered = !suddenTriggered && slope > GRADUAL_SLOPE_PER_MINUTE_THRESHOLD;

        evaluate(event.trackerId(), AnomalyType.SUDDEN, suddenTriggered, event.recordedAt(),
                suddenMessage(instantRate, zScore), BigDecimal.valueOf(zScore));
        evaluate(event.trackerId(), AnomalyType.GRADUAL, gradualTriggered, event.recordedAt(),
                gradualMessage(slope, window.size()), null);
    }

    private void evaluate(String trackerId, AnomalyType type, boolean triggered, Instant ts, String message,
            BigDecimal zScore) {
        Optional<AnomalyEvent> active = anomalyEventRepository
                .findByTrackerIdAndTypeAndStatus(trackerId, type, AnomalyStatus.ACTIVE);

        if (triggered) {
            if (active.isPresent()) {
                active.get().resetCleanStreak();
                return;
            }
            anomalyEventRepository.save(
                    AnomalyEvent.activate(trackerId, ts, type, severityOf(type), message, zScore));
            eventPublisher.publishEvent(
                    new AnomalyDetectedEvent(trackerId, type, severityOf(type), message, ts, AnomalyStatus.ACTIVE));
            return;
        }

        active.ifPresent(anomalyEvent -> {
            anomalyEvent.recordCleanReading();
            if (anomalyEvent.shouldClear(CLEAR_STREAK_THRESHOLD)) {
                anomalyEvent.clear(ts);
                eventPublisher.publishEvent(new AnomalyDetectedEvent(
                        trackerId, type, anomalyEvent.getSeverity(), anomalyEvent.getMessage(), ts,
                        AnomalyStatus.CLEARED));
            }
        });
    }

    private static AnomalySeverity severityOf(AnomalyType type) {
        return type == AnomalyType.SUDDEN ? AnomalySeverity.HIGH : AnomalySeverity.MEDIUM;
    }

    private static String suddenMessage(double instantRate, double zScore) {
        return String.format(Locale.ROOT, "직전 대비 %+.2f℃/분 변화 (z=%.1f)", instantRate, zScore);
    }

    private static String gradualMessage(double slope, int windowSize) {
        return String.format(Locale.ROOT, "최근 %d개 리딩에 걸쳐 %.2f℃/분 상승 추세", windowSize, slope);
    }

    /** baseline(최신 리딩 제외) 대비 최신 온도의 z-score. baseline 표준편차가 0이면 0(무편차) 반환. */
    static double zScore(List<WindowPoint> baseline, double latestTemperature) {
        double mean = baseline.stream().mapToDouble(WindowPoint::temperature).average().orElse(latestTemperature);
        double variance = baseline.stream().mapToDouble(p -> Math.pow(p.temperature() - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) {
            return 0;
        }
        return (latestTemperature - mean) / stdDev;
    }

    /** 직전 리딩 대비 순간 변화율(℃/분). */
    static double instantRatePerMinute(WindowPoint previous, WindowPoint latest) {
        double minutes = (latest.epochMillis() - previous.epochMillis()) / 60_000.0;
        if (minutes <= 0) {
            return 0;
        }
        return (latest.temperature() - previous.temperature()) / minutes;
    }

    /** 윈도우 전체에 대한 최소자승 기울기(℃/분). */
    static double slopePerMinute(List<WindowPoint> window) {
        if (window.size() < 2) {
            return 0;
        }
        long t0 = window.get(0).epochMillis();
        double n = window.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (WindowPoint p : window) {
            double x = (p.epochMillis() - t0) / 60_000.0;
            double y = p.temperature();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) {
            return 0;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }
}
