package com.coldchain.prediction.controller;

import com.coldchain.auth.AdminKeyValidator;
import com.coldchain.common.error.AdminUnauthorizedException;
import com.coldchain.prediction.dto.EvaluationRunResponse;
import com.coldchain.prediction.service.EvaluationRunService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 평가 런 어드민 API(M7) — AdminMetricsController와 같은 X-Admin-Key 패턴. 수동 스냅샷 생성
 * (비교 스크립트용)과 최근 런 목록 조회(리포트 뷰 셀렉터용).
 */
@RestController
public class AdminEvaluationRunController {

    private static final int MAX_LIMIT = 200;

    private final EvaluationRunService evaluationRunService;
    private final AdminKeyValidator adminKeyValidator;

    public AdminEvaluationRunController(EvaluationRunService evaluationRunService,
            AdminKeyValidator adminKeyValidator) {
        this.evaluationRunService = evaluationRunService;
        this.adminKeyValidator = adminKeyValidator;
    }

    public record CreateRunRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            String label,
            String modelVersion) {
    }

    @PostMapping("/api/v1/admin/evaluation-runs")
    public EvaluationRunResponse create(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestBody CreateRunRequest request) {
        requireAdmin(adminKey);
        return evaluationRunService.createManual(request.from(), request.to(), request.label(), request.modelVersion());
    }

    @GetMapping("/api/v1/admin/evaluation-runs")
    public List<EvaluationRunResponse> list(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestParam(required = false) Integer limit) {
        requireAdmin(adminKey);
        int effectiveLimit = limit != null ? Math.min(limit, MAX_LIMIT) : 50;
        return evaluationRunService.list(effectiveLimit);
    }

    private void requireAdmin(String adminKey) {
        if (!adminKeyValidator.isValid(adminKey)) {
            throw new AdminUnauthorizedException("어드민 키가 유효하지 않습니다.");
        }
    }
}
