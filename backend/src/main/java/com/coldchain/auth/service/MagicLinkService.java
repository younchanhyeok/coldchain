package com.coldchain.auth.service;

import com.coldchain.auth.domain.MagicLinkToken;
import com.coldchain.auth.repository.MagicLinkTokenRepository;
import com.coldchain.common.error.MagicLinkExpiredException;
import com.coldchain.common.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MagicLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration EXPIRY_AFTER_DELIVERY = Duration.ofDays(7);

    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final String frontendBaseUrl;

    public MagicLinkService(MagicLinkTokenRepository magicLinkTokenRepository,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** shipment 생성 트랜잭션 안에서 호출 — 발급 시엔 만료 없음(무기한, 배송 완료 시 세팅). */
    @Transactional
    public String issueForShipment(Long shipmentId) {
        String token = "mlk_" + generateToken();
        magicLinkTokenRepository.save(MagicLinkToken.issue(token, shipmentId));
        return frontendBaseUrl + "/t/" + token;
    }

    /** DELIVERED 전이 시 호출 — 그 shipment의 매직링크를 delivered_at+7일에 만료시킨다. */
    @Transactional
    public void expireForShipment(Long shipmentId, Instant deliveredAt) {
        magicLinkTokenRepository.findByShipmentId(shipmentId)
                .ifPresent(token -> token.expireAt(deliveredAt.plus(EXPIRY_AFTER_DELIVERY)));
    }

    /** 무효 토큰은 404(존재 은닉과 같은 원칙), 만료된 토큰은 401 MAGIC_LINK_EXPIRED. */
    @Transactional(readOnly = true)
    public MagicLinkToken resolve(String token) {
        MagicLinkToken magicLinkToken = magicLinkTokenRepository.findById(token)
                .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 링크입니다."));
        if (magicLinkToken.isExpired()) {
            throw new MagicLinkExpiredException("만료된 링크입니다.");
        }
        return magicLinkToken;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
