package com.coldchain.detection.domain;

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
 * 같은 (tracker, type) 이상은 동시에 최대 1건만 ACTIVE — 리딩마다 반복 저장/알림 발행되는 것을
 * 막기 위한 활성/해제 생명주기를 가진다. cleanStreak은 연속으로 조건 미해당인 리딩 수이며,
 * 임계치(AnomalyDetectionService.CLEAR_STREAK_THRESHOLD)에 도달하면 CLEARED로 닫힌다.
 */
@Entity
@Table(name = "anomaly_event")
public class AnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Column(nullable = false)
    private Instant ts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalySeverity severity;

    @Column(length = 500)
    private String message;

    @Column(name = "z_score")
    private BigDecimal zScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyStatus status;

    @Column(name = "clean_streak", nullable = false)
    private int cleanStreak;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    protected AnomalyEvent() {
    }

    public static AnomalyEvent activate(String trackerId, Instant ts, AnomalyType type, AnomalySeverity severity,
            String message, BigDecimal zScore) {
        AnomalyEvent event = new AnomalyEvent();
        event.trackerId = trackerId;
        event.ts = ts;
        event.type = type;
        event.severity = severity;
        event.message = message;
        event.zScore = zScore;
        event.status = AnomalyStatus.ACTIVE;
        event.cleanStreak = 0;
        return event;
    }

    public void resetCleanStreak() {
        this.cleanStreak = 0;
    }

    public void recordCleanReading() {
        this.cleanStreak++;
    }

    public boolean shouldClear(int clearStreakThreshold) {
        return cleanStreak >= clearStreakThreshold;
    }

    public void clear(Instant clearedAt) {
        this.status = AnomalyStatus.CLEARED;
        this.clearedAt = clearedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public Instant getTs() {
        return ts;
    }

    public AnomalyType getType() {
        return type;
    }

    public AnomalySeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getZScore() {
        return zScore;
    }

    public AnomalyStatus getStatus() {
        return status;
    }

    public int getCleanStreak() {
        return cleanStreak;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    @Override
    public String toString() {
        return "AnomalyEvent{id=%s, trackerId=%s, type=%s, status=%s, cleanStreak=%d, ts=%s, clearedAt=%s}"
                .formatted(id, trackerId, type, status, cleanStreak, ts, clearedAt);
    }
}
