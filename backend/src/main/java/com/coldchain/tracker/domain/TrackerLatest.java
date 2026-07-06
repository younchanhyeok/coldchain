package com.coldchain.tracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "tracker_latest")
public class TrackerLatest {

    @Id
    @Column(name = "tracker_id")
    private String trackerId;

    @Column(name = "last_ts")
    private Instant lastTs;

    @Column(name = "last_temp")
    private BigDecimal lastTemp;

    @Column(name = "last_position")
    private Point lastPosition;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrackerLatest() {
    }

    public TrackerLatest(String trackerId) {
        this.trackerId = trackerId;
        this.updatedAt = Instant.now();
    }

    /** 호출 전에 recordedAt이 현재 lastTs보다 최신인지는 서비스 레이어에서 확인한다. */
    public void applyReading(Instant recordedAt, BigDecimal temperature, Point position) {
        this.lastTs = recordedAt;
        this.lastTemp = temperature;
        this.lastPosition = position;
        this.updatedAt = Instant.now();
    }

    public String getTrackerId() {
        return trackerId;
    }

    public Instant getLastTs() {
        return lastTs;
    }

    public BigDecimal getLastTemp() {
        return lastTemp;
    }

    public Point getLastPosition() {
        return lastPosition;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
