package com.coldchain.common.error;

public class OutOfOrderConflictException extends RuntimeException {

    public OutOfOrderConflictException(String message) {
        super(message);
    }
}
