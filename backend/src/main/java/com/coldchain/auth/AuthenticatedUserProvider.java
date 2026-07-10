package com.coldchain.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * DevShipperProvider(M1 임시 조치, 하드코딩 shipperId=1L)를 대체 — JwtAuthenticationFilter가
 * SecurityContext에 채워 넣은 인증된 화주의 userId를 반환한다. 화주=app_user이므로 userId가
 * 곧 shipperId다(별도 클레임 불필요).
 */
@Component
public class AuthenticatedUserProvider {

    public Long shipperId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            // authenticated() 경로는 필터가 통과시킨 요청만 컨트롤러까지 도달하므로 정상 흐름에선
            // 발생하지 않는다 — 발생한다면 설정 실수(permitAll 경로에서 스코핑 서비스를 호출)다.
            throw new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다.");
        }
        return userId;
    }
}
