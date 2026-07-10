package com.coldchain.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 수령기관 무계정 접근 — 토큰 자체가 리소스 식별자(capability)다. 발급 시점(READY/IN_TRANSIT)엔
 * expiresAt이 null(무기한) — 배송 완료(DELIVERED) 전이 시에만 delivered_at+7일로 세팅한다
 * (ShipmentService.updateStatus). "컬럼이 곧 판정 근거"라 만료 규칙이 그 전이 시점 한 곳에만 있다.
 */
@Entity
@Table(name = "magic_link_token")
public class MagicLinkToken {

    @Id
    private String token;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MagicLinkToken() {
    }

    public static MagicLinkToken issue(String token, Long shipmentId) {
        MagicLinkToken magicLinkToken = new MagicLinkToken();
        magicLinkToken.token = token;
        magicLinkToken.shipmentId = shipmentId;
        magicLinkToken.createdAt = Instant.now();
        return magicLinkToken;
    }

    public void expireAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String getToken() {
        return token;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
