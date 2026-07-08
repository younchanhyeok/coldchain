package com.coldchain.alert.domain;

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
 * FR-6/7 알림 발송 이력. "발송 후 저장"이 아니라 PENDING으로 먼저 저장한 뒤 발송을 시도한다 —
 * Slack이 완전히 죽어도 이 행 자체는 남아 알림 탭 타임라인(발생→시도→실패)이 성립하고
 * FR-7 회복탄력성의 실제 증거가 된다.
 */
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(name = "temperature_at_event")
    private BigDecimal temperatureAtEvent;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Alert() {
    }

    public static Alert pending(String trackerId, AlertType type, AlertSeverity severity,
            BigDecimal temperatureAtEvent, String message) {
        Alert alert = new Alert();
        alert.trackerId = trackerId;
        alert.type = type;
        alert.severity = severity;
        alert.temperatureAtEvent = temperatureAtEvent;
        alert.message = message;
        alert.channel = AlertChannel.SLACK;
        alert.status = AlertStatus.PENDING;
        alert.retryCount = 0;
        alert.createdAt = Instant.now();
        return alert;
    }

    public void markSent(int retryCount) {
        this.status = AlertStatus.SENT;
        this.retryCount = retryCount;
    }

    public void markFailed(int retryCount) {
        this.status = AlertStatus.FAILED;
        this.retryCount = retryCount;
    }

    public Long getId() {
        return id;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public AlertType getType() {
        return type;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public BigDecimal getTemperatureAtEvent() {
        return temperatureAtEvent;
    }

    public String getMessage() {
        return message;
    }

    public AlertChannel getChannel() {
        return channel;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
