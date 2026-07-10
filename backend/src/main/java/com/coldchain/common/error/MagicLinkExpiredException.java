package com.coldchain.common.error;

/** 매직링크가 존재는 하지만(발급됐지만) 만료된 경우 — 401 MAGIC_LINK_EXPIRED. 무효 토큰(존재 자체가
 * 없음)은 ResourceNotFoundException(404)으로 별도 처리한다. */
public class MagicLinkExpiredException extends RuntimeException {

    public MagicLinkExpiredException(String message) {
        super(message);
    }
}
