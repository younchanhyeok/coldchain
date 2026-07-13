package com.coldchain.ingest.dto;

import java.time.Instant;
import java.util.List;

/** 배치 수집 응답(M6) — 207 대신 202 + 요약. rejected가 있어도 나머지는 저장된다(부분 성공). */
public record BatchIngestResponse(int accepted, List<RejectedReading> rejected, Instant serverTs) {

    /** index: 요청 배열에서의 위치, code: VALIDATION_FAILED | SEMANTIC_INVALID. */
    public record RejectedReading(int index, String code, String reason) {
    }
}
