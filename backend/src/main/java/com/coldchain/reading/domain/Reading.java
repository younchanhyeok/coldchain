package com.coldchain.reading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "reading")
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "temperature", nullable = false)
    private BigDecimal temperature;

    @Column(name = "position")
    private Point position;

    @Column(name = "server_ts", nullable = false, updatable = false)
    private Instant serverTs;

    protected Reading() {
    }

    public Reading(String trackerId, Instant recordedAt, BigDecimal temperature, Point position) {
        this.trackerId = trackerId;
        this.recordedAt = recordedAt;
        this.temperature = temperature;
        this.position = position;
        this.serverTs = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public Point getPosition() {
        return position;
    }

    public Instant getServerTs() {
        return serverTs;
    }
}
