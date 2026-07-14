package com.coldchain.ingest.controller;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.DeviceKeyUnauthorizedException;
import com.coldchain.common.error.OutOfOrderConflictException;
import com.coldchain.common.error.RequestFieldValidationException;
import com.coldchain.common.error.RequestFieldValidationException.FieldViolation;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.common.error.SemanticInvalidException;
import com.coldchain.ingest.dto.BatchIngestResponse;
import com.coldchain.ingest.dto.BatchIngestResponse.RejectedReading;
import com.coldchain.ingest.dto.IngestAcceptedResponse;
import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.reading.dto.NewReading;
import com.coldchain.reading.service.ReadingService;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerLatestService;
import com.coldchain.tracker.service.TrackerLatestUpsertOutcome;
import com.coldchain.tracker.service.TrackerLatestUpsertResult;
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
import org.locationtech.jts.geom.Point;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
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
    // 디바이스 버퍼 플러시 상정 상한 — 폭주 방지. 초과는 배열 자체를 422로 거절.
    private static final int MAX_BATCH_SIZE = 500;

    private final TrackerRepository trackerRepository;
    private final ReadingService readingService;
    private final TrackerLatestService trackerLatestService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public IngestController(TrackerRepository trackerRepository, ReadingService readingService,
            TrackerLatestService trackerLatestService, ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper, Validator validator) {
        this.trackerRepository = trackerRepository;
        this.readingService = readingService;
        this.trackerLatestService = trackerLatestService;
        this.eventPublisher = eventPublisher;
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

        BigDecimal temperature = BigDecimal.valueOf(request.temperature());
        Point position = GeoPoints.of(request.lat(), request.lon());

        readingService.save(tracker.getId(), request.recordedAt(), temperature, position);

        upsertLatestAndPublish(tracker, request.recordedAt(), temperature, position);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestAcceptedResponse(true, Instant.now()));
    }

    /**
     * 배치(M6): 요소별 검증 실패는 rejected[]로 모으고 나머지는 저장하는 부분 성공 —
     * 전체는 항상 202다(401/404 같은 요청 단위 실패 제외). 원시 저장은 JDBC 배치 1왕복,
     * latest upsert는 최신 recordedAt 1건으로 collapse — 같은 규칙을 PR3 Kafka 컨슈머가 물려받는다.
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

        readingService.saveBatch(tracker.getId(), valid.stream()
                .map(r -> new NewReading(r.recordedAt(), BigDecimal.valueOf(r.temperature()), r.lat(), r.lon()))
                .toList());

        valid.stream().max(Comparator.comparing(ReadingIngestRequest::recordedAt)).ifPresent(newest ->
                upsertLatestAndPublish(tracker, newest.recordedAt(),
                        BigDecimal.valueOf(newest.temperature()), GeoPoints.of(newest.lat(), newest.lon())));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BatchIngestResponse(valid.size(), rejected, Instant.now()));
    }

    private void upsertLatestAndPublish(Tracker tracker, Instant recordedAt, BigDecimal temperature, Point position) {
        TrackerLatestUpsertResult result = trackerLatestService.upsert(
                tracker.getId(), recordedAt, temperature, position);

        if (result.outcome() == TrackerLatestUpsertOutcome.CONFLICT) {
            throw new OutOfOrderConflictException(
                    "최신 상태 갱신 충돌로 재시도를 모두 소진했습니다: " + tracker.getId());
        }

        if (result.outcome() == TrackerLatestUpsertOutcome.UPDATED) {
            publishReadingRecorded(tracker.getId(), temperature, position, recordedAt, tracker.getThresholdTemp(),
                    result.previousTemperature());
        }
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

    private void publishReadingRecorded(String trackerId, BigDecimal temperature, Point position,
            Instant recordedAt, BigDecimal thresholdTemp, BigDecimal previousTemperature) {
        boolean wasOverThreshold = previousTemperature != null && previousTemperature.compareTo(thresholdTemp) > 0;
        boolean isOverThreshold = temperature.compareTo(thresholdTemp) > 0;
        boolean justBreached = isOverThreshold && !wasOverThreshold;

        eventPublisher.publishEvent(
                new ReadingRecordedEvent(trackerId, temperature, position, recordedAt, thresholdTemp, justBreached));
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
    }
}
