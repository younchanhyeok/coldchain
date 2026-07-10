package com.coldchain.auth.service;

import com.coldchain.auth.domain.AppUser;
import com.coldchain.auth.dto.TokenResponse;
import com.coldchain.auth.repository.AppUserRepository;
import com.coldchain.common.error.AuthUnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // 이메일 없음/비밀번호 불일치를 같은 메시지·같은 401로 — 계정 존재 여부를 은닉한다(명세 §2).
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String rawPassword) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new AuthUnauthorizedException(LOGIN_FAILED));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthUnauthorizedException(LOGIN_FAILED);
        }
        return issueTokens(user);
    }

    /**
     * refresh는 무상태 JWT — DB 저장·폐기(revocation) 없음. 탈취 시 최대 14일 창이 남는 것은
     * 인지한 트레이드오프(솔로 포트폴리오 규모에서 토큰 저장소+정리 잡의 비용이 더 큼, README 명시).
     * 매번 새 쌍을 발급하므로 클라이언트는 두 토큰을 모두 교체한다.
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        JwtTokenProvider.TokenPayload payload = jwtTokenProvider.parseRefreshToken(refreshToken);
        AppUser user = appUserRepository.findById(payload.userId())
                .orElseThrow(() -> new AuthUnauthorizedException("유효하지 않은 토큰입니다."));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(AppUser user) {
        return new TokenResponse(
                jwtTokenProvider.createAccessToken(user.getId(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getId(), user.getRole()),
                user.getRole().name(),
                user.getCompanyName());
    }
}
