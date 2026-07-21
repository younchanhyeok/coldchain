package com.coldchain.reading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

/**
 * hypertable 전환(M6 PR4) 이후 **읽기 전용 엔티티**다 — 복합키(@IdClass)에는 IDENTITY 채번을
 * 쓸 수 없어(@GeneratedValue 미지원) JPA로 persist하면 id가 null이라 실패한다. 쓰기는 전부
 * ReadingBatchWriter(JDBC, id는 DB DEFAULT nextval)로 — PR3에서 이미 일원화된 경로.
 */
@Entity
@Table(name = "reading")
@IdClass(ReadingId.class)
public class Reading {

    @Id
    private Long id;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Id
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "temperature", nullable = false)
    private BigDecimal temperature;

    @Column(name = "position")
    private Point position;

    @Column(name = "server_ts", nullable = false, updatable = false)
    private Instant serverTs;

    // 외기 센서값(M7 v2용, nullable). 쓰기는 ReadingBatchWriter가 담당하므로 여기선 읽기 매핑만 —
    // 기존 4-인자 생성자(테스트 픽스처)는 이 값을 안 채우고 null로 둔다.
    @Column(name = "ambient_temp")
    private BigDecimal ambientTemp;

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

    public BigDecimal getAmbientTemp() {
        return ambientTemp;
    }
}
