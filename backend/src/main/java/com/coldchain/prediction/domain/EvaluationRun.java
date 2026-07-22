package com.coldchain.prediction.domain;

import com.coldchain.prediction.dto.PredictionMetricsResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 평가 런 — 특정 기간·모델버전의 예측 지표 스냅샷(M7). 지표는 PredictionMetricsService.getMetrics가
 * 계산하고, 이 엔티티는 그 결과에서 집계값만 떼어 "언제·어떤 라벨로 잰 런"이라는 정체성과 함께 고정한다.
 * episodes 목록은 저장하지 않는다(라이브 데이터로 재조회 가능 — 스냅샷은 집계 수치가 목적).
 */
@Entity
@Table(name = "evaluation_run")
public class EvaluationRun {

    public enum TriggerType {
        SCHEDULED, MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label")
    private String label;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "model_version")
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Column(name = "total_predictions", nullable = false)
    private int totalPredictions;

    @Column(name = "true_positives", nullable = false)
    private int truePositives;

    @Column(name = "false_positives", nullable = false)
    private int falsePositives;

    @Column(name = "missed_breaches", nullable = false)
    private int missedBreaches;

    @Column(name = "hit_rate", nullable = false)
    private double hitRate;

    @Column(name = "false_positive_rate", nullable = false)
    private double falsePositiveRate;

    @Column(name = "avg_lead_time_minutes")
    private Double avgLeadTimeMinutes;

    @Column(name = "median_lead_time_minutes")
    private Double medianLeadTimeMinutes;

    @Column(name = "avg_breach_timing_error_minutes")
    private Double avgBreachTimingErrorMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvaluationRun() {
    }

    /** getMetrics 결과에서 집계값을 떠 스냅샷을 만든다 — 지표 계산은 재사용, 저장 정체성만 여기서. */
    public static EvaluationRun snapshot(String label, TriggerType triggerType, PredictionMetricsResponse metrics) {
        EvaluationRun run = new EvaluationRun();
        run.label = label;
        run.triggerType = triggerType;
        run.modelVersion = metrics.modelVersion();
        run.periodStart = metrics.period().from();
        run.periodEnd = metrics.period().to();
        run.totalPredictions = metrics.totalPredictions();
        run.truePositives = metrics.truePositives();
        run.falsePositives = metrics.falsePositives();
        run.missedBreaches = metrics.missedBreaches();
        run.hitRate = metrics.hitRate();
        run.falsePositiveRate = metrics.falsePositiveRate();
        run.avgLeadTimeMinutes = metrics.avgLeadTimeMinutes();
        run.medianLeadTimeMinutes = metrics.medianLeadTimeMinutes();
        run.avgBreachTimingErrorMinutes = metrics.avgBreachTimingErrorMinutes();
        run.createdAt = Instant.now();
        return run;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public int getTotalPredictions() {
        return totalPredictions;
    }

    public int getTruePositives() {
        return truePositives;
    }

    public int getFalsePositives() {
        return falsePositives;
    }

    public int getMissedBreaches() {
        return missedBreaches;
    }

    public double getHitRate() {
        return hitRate;
    }

    public double getFalsePositiveRate() {
        return falsePositiveRate;
    }

    public Double getAvgLeadTimeMinutes() {
        return avgLeadTimeMinutes;
    }

    public Double getMedianLeadTimeMinutes() {
        return medianLeadTimeMinutes;
    }

    public Double getAvgBreachTimingErrorMinutes() {
        return avgBreachTimingErrorMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
