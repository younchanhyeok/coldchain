package com.coldchain.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * M4 최소 구현 — 어드민 키 문자열 비교만("어드민 API — MVP는 이 1개(예측 평가지표)만" 스코프).
 * JWT(화주)·매직링크(수령기관)·역할별 인가 스코핑은 M5.
 * 미설정(기본 빈 값)이면 어떤 키를 보내도 거부한다 — 실수로 열린 채 배포되는 것을 방지.
 */
@Component
public class AdminKeyValidator {

    private final String adminKey;

    public AdminKeyValidator(@Value("${app.auth.admin-key:}") String adminKey) {
        this.adminKey = adminKey;
    }

    public boolean isValid(String providedKey) {
        return !adminKey.isBlank() && adminKey.equals(providedKey);
    }
}
