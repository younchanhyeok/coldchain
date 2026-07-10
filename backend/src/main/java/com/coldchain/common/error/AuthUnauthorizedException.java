package com.coldchain.common.error;

/** 로그인 실패·토큰 무효 — 사유(계정 없음/비번 불일치/만료/변조)를 구분하지 않는 단일 401. */
public class AuthUnauthorizedException extends RuntimeException {

    public AuthUnauthorizedException(String message) {
        super(message);
    }
}
