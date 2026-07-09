package com.coldchain.prediction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 평가 지표(리드타임·오탐률)의 "실제 이탈" 원천 — alert(BREACH)는 dedup(10분)으로 누락될 수
 * 있어 평가 원천으로 쓰기엔 부정직하다. justBreached 전이마다 dedup 없이 그대로 기록한다.
 */
@Entity
@Table(name = "breach_event")
public class BreachEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Column(nullable = false)
    private Instant ts;

    protected BreachEvent() {
    }

    public BreachEvent(String trackerId, Instant ts) {
        this.trackerId = trackerId;
        this.ts = ts;
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
}
