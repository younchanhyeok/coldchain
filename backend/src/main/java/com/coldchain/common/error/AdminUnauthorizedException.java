package com.coldchain.common.error;

public class AdminUnauthorizedException extends RuntimeException {

    public AdminUnauthorizedException(String message) {
        super(message);
    }
}
