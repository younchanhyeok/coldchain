package com.coldchain.ingest.controller;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.DeviceKeyUnauthorizedException;
import com.coldchain.common.error.OutOfOrderConflictException;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.common.error.SemanticInvalidException;
import com.coldchain.ingest.dto.IngestAcceptedResponse;
import com.coldchain.ingest.dto.ReadingIngestRequest;
import com.coldchain.reading.service.ReadingService;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import com.coldchain.tracker.service.TrackerLatestService;
import com.coldchain.tracker.service.TrackerLatestUpsertOutcome;
import com.coldchain.tracker.service.TrackerService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trackers/{trackerId}/readings")
public class IngestController {

    private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("-90");
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("60");
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final TrackerRepository trackerRepository;
    private final ReadingService readingService;
    private final TrackerLatestService trackerLatestService;

    public IngestController(TrackerRepository trackerRepository, ReadingService readingService,
            TrackerLatestService trackerLatestService) {
        this.trackerRepository = trackerRepository;
        this.readingService = readingService;
        this.trackerLatestService = trackerLatestService;
    }

    @PostMapping
    public ResponseEntity<IngestAcceptedResponse> ingest(
            @PathVariable String trackerId,
            @RequestHeader("X-Device-Key") String deviceKey,
            @Valid @RequestBody ReadingIngestRequest request) {

        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId));

        if (!tracker.getDeviceKeyHash().equals(TrackerService.hashDeviceKey(deviceKey))) {
            throw new DeviceKeyUnauthorizedException("디바이스 키가 일치하지 않습니다.");
        }

        validate(request);

        BigDecimal temperature = BigDecimal.valueOf(request.temperature());
        Point position = GeoPoints.of(request.lat(), request.lon());

        readingService.save(trackerId, request.recordedAt(), temperature, position);

        TrackerLatestUpsertOutcome outcome = trackerLatestService.upsert(
                trackerId, request.recordedAt(), temperature, position);

        if (outcome == TrackerLatestUpsertOutcome.CONFLICT) {
            throw new OutOfOrderConflictException(
                    "최신 상태 갱신 충돌로 재시도를 모두 소진했습니다: " + trackerId);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestAcceptedResponse(true, Instant.now()));
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
