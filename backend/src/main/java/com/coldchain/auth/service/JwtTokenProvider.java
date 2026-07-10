package com.coldchain.auth.service;

import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.common.error.AuthUnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증 (HS256). 시크릿 미설정·32바이트 미만이면 기동 자체를 거부한다(fail-fast) —
 * AdminKeyValidator의 "미설정=거부"와 같은 철학이되, JWT는 미설정 시 로그인 자체가 불가능하므로
 * 요청 시점이 아니라 기동 시점에 실패시켜 설정 누락을 즉시 드러낸다. 랜덤 생성 폴백은 두지
 * 않는다 — 재기동마다 전 토큰이 무효화되고 설정 누락이 은폐된다.
 *
 * typ 클레임(access|refresh)으로 refresh 토큰을 access로 유용하는 것을 차단한다.
 */
@Component
public class JwtTokenProvider {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    static final String TYP_ACCESS = "access";
    static final String TYP_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32; // HS256 키 최소 길이

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.auth.jwt-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.auth.jwt-secret(JWT_SECRET)이 설정되지 않았습니다 — 32바이트 이상의 시크릿이 필요합니다.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.auth.jwt-secret은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다(현재 " + bytes.length + "바이트).");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public record TokenPayload(Long userId, AppUserRole role) {
    }

    public String createAccessToken(Long userId, AppUserRole role) {
        return create(userId, role, TYP_ACCESS, ACCESS_TTL);
    }

    public String createRefreshToken(Long userId, AppUserRole role) {
        return create(userId, role, TYP_REFRESH, REFRESH_TTL);
    }

    /** access 토큰으로만 인증을 통과시킨다 — refresh를 Authorization 헤더에 넣으면 401. */
    public TokenPayload parseAccessToken(String token) {
        return parse(token, TYP_ACCESS);
    }

    /** refresh 엔드포인트 전용 — access를 refresh 자리에 넣으면 401. */
    public TokenPayload parseRefreshToken(String token) {
        return parse(token, TYP_REFRESH);
    }

    private String create(Long userId, AppUserRole role, String typ, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("typ", typ)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private TokenPayload parse(String token, String expectedTyp) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            // 만료·서명 불일치·형식 오류를 구분해서 알려주지 않는다 — 공격자에게 힌트가 된다.
            throw new AuthUnauthorizedException("유효하지 않은 토큰입니다.");
        }
        if (!expectedTyp.equals(claims.get("typ", String.class))) {
            throw new AuthUnauthorizedException("유효하지 않은 토큰입니다.");
        }
        try {
            return new TokenPayload(Long.parseLong(claims.getSubject()), AppUserRole.valueOf(claims.get("role", String.class)));
        } catch (RuntimeException e) {
            throw new AuthUnauthorizedException("유효하지 않은 토큰입니다.");
        }
    }
}
