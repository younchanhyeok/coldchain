package com.coldchain.common.error;

import java.util.List;

/**
 * 수동 필드 검증 실패 — @Valid(MethodArgumentNotValidException)와 같은 400/VALIDATION_FAILED
 * 응답을 내기 위한 예외. 수집 API가 단건/배치를 한 URL에서 받기 위해 body를 JsonNode로 받으면서
 * (M6 배치 전송) 단건 경로의 바인딩 검증이 수동으로 바뀌었는데, 에러 계약은 그대로 유지해야 한다.
 */
public class RequestFieldValidationException extends RuntimeException {

    /** field: 위반 필드명, reason: 사유 — 기존 400 응답의 errors[] 항목과 같은 shape. */
    public record FieldViolation(String field, String reason) {
    }

    private final List<FieldViolation> violations;

    public RequestFieldValidationException(List<FieldViolation> violations) {
        super("요청 필드 검증에 실패했습니다.");
        this.violations = violations;
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
