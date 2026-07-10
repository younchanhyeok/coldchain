package com.coldchain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.auth.service.JwtTokenProvider;
import com.coldchain.common.error.AuthUnauthorizedException;
import org.junit.jupiter.api.Test;

/** 순수 로직 단위 테스트 — Spring/Docker 없이 로컬에서 돈다. */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-at-least-32-bytes!!";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET);

    @Test
    void accessToken_roundTrip() {
        String token = provider.createAccessToken(7L, AppUserRole.SHIPPER);
        JwtTokenProvider.TokenPayload payload = provider.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.role()).isEqualTo(AppUserRole.SHIPPER);
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken() {
        String refresh = provider.createRefreshToken(7L, AppUserRole.SHIPPER);

        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                .isInstanceOf(AuthUnauthorizedException.class);
    }

    @Test
    void accessToken_cannotBeUsedAsRefreshToken() {
        String access = provider.createAccessToken(7L, AppUserRole.SHIPPER);

        assertThatThrownBy(() -> provider.parseRefreshToken(access))
                .isInstanceOf(AuthUnauthorizedException.class);
    }

    @Test
    void tamperedToken_rejected() {
        String token = provider.createAccessToken(7L, AppUserRole.SHIPPER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(AuthUnauthorizedException.class);
    }

    @Test
    void tokenSignedWithDifferentSecret_rejected() {
        JwtTokenProvider other = new JwtTokenProvider("another-jwt-secret-also-32-bytes-long!!!");
        String foreign = other.createAccessToken(7L, AppUserRole.SHIPPER);

        assertThatThrownBy(() -> provider.parseAccessToken(foreign))
                .isInstanceOf(AuthUnauthorizedException.class);
    }

    @Test
    void missingSecret_failsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
