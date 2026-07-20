package com.coldchain.ingest.controller;

import com.coldchain.common.error.DeviceKeyUnauthorizedException;
import com.coldchain.common.error.RequestFieldValidationException;
import com.coldchain.common.error.RequestFieldValidationException.FieldViolation;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.common.error.SemanticInvalidException;
import com.coldchain.ingest.dto.BatchIngestResponse;
import com.coldchain.ingest.dto.BatchIngestResponse.RejectedReading;
import com.coldchain.ingest.dto.IngestAcceptedResponse;
import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.ingest.pipeline.IngestPipeline;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 API 진입점 — 인증(디바이스 키)·검증까지만 하고, 저장 경로는 IngestPipeline에 위임한다
 * (M6 PR3: direct=동기 저장 | kafka=브로커 발행, app.ingest.mode 토글).
 *
 * 단건과 배치(배열 body, M6)를 같은 URL에서 받는다 — body shape으로는 핸들러를 나눌 수 없어
 * JsonNode로 받아 분기하고, 단건 경로의 필드 검증은 @Valid 대신 Validator 수동 호출로
 * 같은 400/VALIDATION_FAILED 계약을 유지한다.
 */
@RestController
@RequestMapping("/api/v1/trackers/{trackerId}/readings")
public class IngestController {

    private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("-90");
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("60");
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    // 과거 하한(M6 PR4): recorded_at이 hypertable 파티션 컬럼이 되면서 시계 리셋된 디바이스가
    // epoch급 timestamp를 보내면 날짜당 chunk가 물리 생성된다(카탈로그 비대·락). 디바이스가
    // 오프라인 버퍼링 후 몰아 보내는 정상 케이스(배치 API)는 며칠 안쪽이므로 7일이면 넉넉하다.
    private static final Duration MAX_PAST_SKEW = Duration.ofDays(7);
    // 디바이스 버퍼 플러시 상정 상한 — 폭주 방지. 초과는 배열 자체를 422로 거절.
    private static final int MAX_BATCH_SIZE = 500;

    private final TrackerRepository trackerRepository;
    private final IngestPipeline ingestPipeline;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public IngestController(TrackerRepository trackerRepository, IngestPipeline ingestPipeline,
            ObjectMapper objectMapper, Validator validator) {
        this.trackerRepository = trackerRepository;
        this.ingestPipeline = ingestPipeline;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<?> ingest(
            @PathVariable String trackerId,
            @RequestHeader("X-Device-Key") String deviceKey,
            @RequestBody JsonNode body) {

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId));

        if (!tracker.getDeviceKeyHash().equals(TrackerService.hashDeviceKey(deviceKey))) {
            throw new DeviceKeyUnauthorizedException("디바이스 키가 일치하지 않습니다.");
        }

        if (body.isArray()) {
            return ingestBatch(tracker, body);
        }
        return ingestSingle(tracker, body);
    }

    private ResponseEntity<IngestAcceptedResponse> ingestSingle(Tracker tracker, JsonNode body) {
        ReadingIngestRequest request = bind(body);
        List<FieldViolation> violations = fieldViolations(request);
        if (!violations.isEmpty()) {
            throw new RequestFieldValidationException(violations);
        }
        validate(request);

        ingestPipeline.ingest(tracker, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestAcceptedResponse(true, Instant.now()));
    }

    /**
     * 배치(M6): 요소별 검증 실패는 rejected[]로 모으고 나머지는 처리하는 부분 성공 —
     * 전체는 항상 202다(401/404 같은 요청 단위 실패 제외).
     */
    private ResponseEntity<BatchIngestResponse> ingestBatch(Tracker tracker, JsonNode body) {
        if (body.size() > MAX_BATCH_SIZE) {
            throw new SemanticInvalidException("배치는 한 번에 최대 " + MAX_BATCH_SIZE + "건입니다: " + body.size());
        }

        List<ReadingIngestRequest> valid = new ArrayList<>();
        List<RejectedReading> rejected = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            try {
                ReadingIngestRequest request = bind(body.get(i));
                List<FieldViolation> violations = fieldViolations(request);
                if (!violations.isEmpty()) {
                    rejected.add(new RejectedReading(i, "VALIDATION_FAILED",
                            violations.get(0).field() + ": " + violations.get(0).reason()));
                    continue;
                }
                validate(request);
                valid.add(request);
            } catch (RequestFieldValidationException e) {
                rejected.add(new RejectedReading(i, "VALIDATION_FAILED", e.getMessage()));
            } catch (SemanticInvalidException e) {
                rejected.add(new RejectedReading(i, "SEMANTIC_INVALID", e.getMessage()));
            }
        }

        if (!valid.isEmpty()) {
            ingestPipeline.ingestBatch(tracker, valid);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchIngestResponse(valid.size(), rejected, Instant.now()));
    }

    private ReadingIngestRequest bind(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, ReadingIngestRequest.class);
        } catch (Exception e) {
            throw new RequestFieldValidationException(
                    List.of(new FieldViolation("body", "필드 타입이 올바르지 않습니다.")));
        }
    }

    private List<FieldViolation> fieldViolations(ReadingIngestRequest request) {
        return validator.validate(request).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
    }

    private void validate(ReadingIngestRequest request) {
        BigDecimal temperature = BigDecimal.valueOf(request.temperature());
        if (temperature.compareTo(MIN_TEMPERATURE) < 0 || temperature.compareTo(MAX_TEMPERATURE) > 0) {
            throw new SemanticInvalidException(
                    "온도는 -90~60도 범위여야 합니다: " + request.temperature());
        }
        if (request.recordedAt().isAfter(Instant.now().plus(MAX_FUTURE_SKEW))) {
            throw new SemanticInvalidException(
                    "recordedAt이 미래 5분을 초과할 수 없습니다: " + request.recordedAt());
        }
        if (request.recordedAt().isBefore(Instant.now().minus(MAX_PAST_SKEW))) {
            throw new SemanticInvalidException(
                    "recordedAt이 과거 7일을 초과할 수 없습니다: " + request.recordedAt());
        }
    }
}
