package com.coldchain.common.error;

public class DeviceKeyUnauthorizedException extends RuntimeException {

    public DeviceKeyUnauthorizedException(String message) {
        super(message);
    }
}
