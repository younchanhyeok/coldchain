package com.coldchain.common.error;

/** 수집 큐(Kafka) 발행 실패 — 503. 브로커는 M6부터 수집 경로의 일부라 저장 보장 없이 202를 줄 수 없다. */
public class IngestUnavailableException extends RuntimeException {

    public IngestUnavailableException(String message) {
        super(message);
    }
}
