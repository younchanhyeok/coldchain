package com.coldchain.tracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tracker")
public class Tracker {

    @Id
    private String id;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "threshold_temp", nullable = false)
    private BigDecimal thresholdTemp;

    @Column(name = "device_key_hash", nullable = false)
    private String deviceKeyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tracker() {
    }

    public Tracker(String id, Long shipperId, String productName, BigDecimal thresholdTemp, String deviceKeyHash) {
        this.id = id;
        this.shipperId = shipperId;
        this.productName = productName;
        this.thresholdTemp = thresholdTemp;
        this.deviceKeyHash = deviceKeyHash;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getThresholdTemp() {
        return thresholdTemp;
    }

    public String getDeviceKeyHash() {
        return deviceKeyHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
