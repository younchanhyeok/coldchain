package com.coldchain.auth.dto;

/** login/refresh 공용 응답 — 명세 §2. companyName은 토큰 클레임이 아니라 응답 body로만 내려간다. */
public record TokenResponse(String accessToken, String refreshToken, String role, String companyName) {
}
