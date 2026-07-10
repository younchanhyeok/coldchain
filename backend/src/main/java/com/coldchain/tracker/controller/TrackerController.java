package com.coldchain.tracker.controller;

import com.coldchain.auth.TrackerOwnershipGuard;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.tracker.domain.TrackerStatus;
import com.coldchain.tracker.dto.TrackerDetailResponse;
import com.coldchain.tracker.dto.TrackerListResponse;
import com.coldchain.tracker.dto.TrackerRegisterRequest;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.coldchain.tracker.service.TrackerQueryService;
import com.coldchain.tracker.service.TrackerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trackers")
public class TrackerController {

    private final TrackerService trackerService;
    private final TrackerQueryService trackerQueryService;
    private final TrackerOwnershipGuard trackerOwnershipGuard;

    public TrackerController(TrackerService trackerService, TrackerQueryService trackerQueryService,
            TrackerOwnershipGuard trackerOwnershipGuard) {
        this.trackerService = trackerService;
        this.trackerQueryService = trackerQueryService;
        this.trackerOwnershipGuard = trackerOwnershipGuard;
    }

    @PostMapping
    public ResponseEntity<TrackerRegisterResponse> register(@Valid @RequestBody TrackerRegisterRequest request) {
        TrackerRegisterResponse response = trackerService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/trackers/" + response.trackerId())).body(response);
    }

    @GetMapping
    public TrackerListResponse list(
            @RequestParam(required = false) TrackerStatus status,
            @RequestParam(required = false, defaultValue = "IN_TRANSIT") ShipmentStatus shipmentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return trackerQueryService.list(status, shipmentStatus, page, size);
    }

    @GetMapping("/{trackerId}")
    public TrackerDetailResponse detail(@PathVariable String trackerId) {
        trackerOwnershipGuard.assertOwnedByCurrentShipper(trackerId);
        return trackerQueryService.detail(trackerId);
    }
}
