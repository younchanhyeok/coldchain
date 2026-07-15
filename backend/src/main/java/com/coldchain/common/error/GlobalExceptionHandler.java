package com.coldchain.common.error;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage());
    }

    @ExceptionHandler(SemanticInvalidException.class)
    public ProblemDetail handleSemanticInvalid(SemanticInvalidException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "SEMANTIC_INVALID", ex.getMessage());
    }

    @ExceptionHandler(DeviceKeyUnauthorizedException.class)
    public ProblemDetail handleDeviceKeyUnauthorized(DeviceKeyUnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(AdminUnauthorizedException.class)
    public ProblemDetail handleAdminUnauthorized(AdminUnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(AuthUnauthorizedException.class)
    public ProblemDetail handleAuthUnauthorized(AuthUnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(MagicLinkExpiredException.class)
    public ProblemDetail handleMagicLinkExpired(MagicLinkExpiredException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "MAGIC_LINK_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(OutOfOrderConflictException.class)
    public ProblemDetail handleOutOfOrderConflict(OutOfOrderConflictException ex) {
        return problem(HttpStatus.CONFLICT, "READING_OUT_OF_ORDER", ex.getMessage());
    }

    /** 수집 큐(Kafka) 발행 실패 — M6부터 브로커는 수집 경로의 일부(저장 보장 없이 202를 줄 수 없다). */
    @ExceptionHandler(IngestUnavailableException.class)
    public ProblemDetail handleIngestUnavailable(IngestUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "INGEST_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailed(MethodArgumentNotValidException ex) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 필드 검증에 실패했습니다.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "reason", String.valueOf(fe.getDefaultMessage())))
                .toList();
        detail.setProperty("errors", errors);
        return detail;
    }

    /** 수동 검증(수집 API 단건/배치 분기) — @Valid 실패와 같은 400/VALIDATION_FAILED 계약 유지. */
    @ExceptionHandler(RequestFieldValidationException.class)
    public ProblemDetail handleRequestFieldValidation(RequestFieldValidationException ex) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
        List<Map<String, String>> errors = ex.getViolations().stream()
                .map(v -> Map.of("field", v.field(), "reason", v.reason()))
                .toList();
        detail.setProperty("errors", errors);
        return detail;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://coldchain.dev/errors/" + code.toLowerCase().replace('_', '-')));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
