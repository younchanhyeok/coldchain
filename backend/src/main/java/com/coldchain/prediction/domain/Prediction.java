package com.coldchain.prediction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * FR-5 예측 에피소드 — 최초 경고(ACTIVE 생성) 이후 리딩마다 같은 행을 갱신한다("에피소드"
 * 단위). {@code createdAt}(최초 경고 시각)은 리드타임 집계의 기준이라 절대 바뀌지 않고,
 * {@code anchorTs}/{@code anchorTemperature}만 갱신마다 최신 실측으로 교체된다 — anchor를
 * 안 바꾸면 forecast 점선이 낡은 지점에서 출발해 갱신된 predictedBreachAt과 어긋난다.
 */
@Entity
@Table(name = "prediction")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "predicted_breach_at")
    private Instant predictedBreachAt;

    @Column(name = "threshold_temp")
    private BigDecimal thresholdTemp;

    @Column(name = "slope_per_minute")
    private BigDecimal slopePerMinute;

    @Column(name = "model_version")
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PredictionStatus status;

    @Column(name = "anchor_ts")
    private Instant anchorTs;

    @Column(name = "anchor_temperature")
    private BigDecimal anchorTemperature;

    @Column(name = "calm_streak", nullable = false)
    private int calmStreak;

    @Column(name = "breached_at")
    private Instant breachedAt;

    protected Prediction() {
    }

    public static Prediction activate(String trackerId, Instant ts, BigDecimal thresholdTemp,
            Instant predictedBreachAt, BigDecimal slopePerMinute, String modelVersion, BigDecimal anchorTemperature) {
        Prediction prediction = new Prediction();
        prediction.trackerId = trackerId;
        prediction.createdAt = ts;
        prediction.thresholdTemp = thresholdTemp;
        prediction.predictedBreachAt = predictedBreachAt;
        prediction.slopePerMinute = slopePerMinute;
        prediction.modelVersion = modelVersion;
        prediction.status = PredictionStatus.ACTIVE;
        prediction.anchorTs = ts;
        prediction.anchorTemperature = anchorTemperature;
        prediction.calmStreak = 0;
        return prediction;
    }

    /** 추세가 계속 이탈 방향으로 확인될 때마다 anchor·예상시각을 최신 실측으로 갱신한다. */
    public void refresh(Instant ts, Instant predictedBreachAt, BigDecimal slopePerMinute, BigDecimal anchorTemperature) {
        this.predictedBreachAt = predictedBreachAt;
        this.slopePerMinute = slopePerMinute;
        this.anchorTs = ts;
        this.anchorTemperature = anchorTemperature;
        this.calmStreak = 0; // 이탈 추세가 다시 확인됐으므로 완화 카운트 리셋
    }

    public void recordCalmReading() {
        this.calmStreak++;
    }

    /** 연속 N회 추세 완화가 확인돼야 취소한다 — 1회 진동으로 취소·재경고가 도배되지 않게. */
    public boolean shouldCancel(int calmStreakThreshold) {
        return calmStreak >= calmStreakThreshold;
    }

    public void cancel() {
        this.status = PredictionStatus.CANCELED;
    }

    /** 급변(SUDDEN) 이상 감지 시 — 선형 추세 가정이 깨졌으므로 예측을 신뢰할 수 없다(안전한 실패). */
    public void invalidate() {
        this.status = PredictionStatus.INVALIDATED;
    }

    /** 예상 이탈 시각을 넘기고도 이탈이 없었다 — 예측이 빗나간 것(오탐률 집계 대상). */
    public void expire() {
        this.status = PredictionStatus.EXPIRED;
    }

    /** 실제로 이탈했다 — 적중 종결(리드타임 = breachedAt - createdAt). */
    public void markBreached(Instant ts) {
        this.status = PredictionStatus.BREACHED;
        this.breachedAt = ts;
    }

    public Long getId() {
        return id;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPredictedBreachAt() {
        return predictedBreachAt;
    }

    public BigDecimal getThresholdTemp() {
        return thresholdTemp;
    }

    public BigDecimal getSlopePerMinute() {
        return slopePerMinute;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public PredictionStatus getStatus() {
        return status;
    }

    public Instant getAnchorTs() {
        return anchorTs;
    }

    public BigDecimal getAnchorTemperature() {
        return anchorTemperature;
    }

    public int getCalmStreak() {
        return calmStreak;
    }

    public Instant getBreachedAt() {
        return breachedAt;
    }
}
