package com.coldchain.detection.controller;

import com.coldchain.detection.domain.AnomalyType;
import com.coldchain.detection.dto.AnomaliesResponse;
import com.coldchain.detection.dto.AnomalyResponse;
import com.coldchain.detection.repository.AnomalyEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnomalyController {

    private static final Duration DEFAULT_RANGE = Duration.ofHours(6);

    private final AnomalyEventRepository anomalyEventRepository;

    public AnomalyController(AnomalyEventRepository anomalyEventRepository) {
        this.anomalyEventRepository = anomalyEventRepository;
    }

    @GetMapping("/api/v1/trackers/{trackerId}/anomalies")
    public AnomaliesResponse getAnomalies(
            @PathVariable String trackerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) AnomalyType type) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_RANGE);

        var events = type != null
                ? anomalyEventRepository.findByTrackerIdAndTypeAndTsBetweenOrderByTsDesc(
                        trackerId, type, effectiveFrom, effectiveTo)
                : anomalyEventRepository.findByTrackerIdAndTsBetweenOrderByTsDesc(
                        trackerId, effectiveFrom, effectiveTo);

        return new AnomaliesResponse(events.stream().map(AnomalyResponse::from).toList());
    }
}
