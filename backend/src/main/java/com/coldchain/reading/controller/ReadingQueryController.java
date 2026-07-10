package com.coldchain.reading.controller;

import com.coldchain.auth.TrackerOwnershipGuard;
import com.coldchain.reading.dto.ReadingSeriesResponse;
import com.coldchain.reading.service.ReadingService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trackers/{trackerId}/readings")
public class ReadingQueryController {

    private static final Duration DEFAULT_RANGE = Duration.ofHours(6);
    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;

    private final ReadingService readingService;
    private final TrackerOwnershipGuard trackerOwnershipGuard;

    public ReadingQueryController(ReadingService readingService, TrackerOwnershipGuard trackerOwnershipGuard) {
        this.readingService = readingService;
        this.trackerOwnershipGuard = trackerOwnershipGuard;
    }

    @GetMapping
    public ReadingSeriesResponse query(
            @PathVariable String trackerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit) {
        trackerOwnershipGuard.assertOwnedByCurrentShipper(trackerId);
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_RANGE);
        int effectiveLimit = limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;

        return readingService.query(trackerId, effectiveFrom, effectiveTo, effectiveLimit);
    }
}
