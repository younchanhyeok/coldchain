package com.coldchain.tracker.controller;

import com.coldchain.tracker.dto.TrackerRegisterRequest;
import com.coldchain.tracker.dto.TrackerRegisterResponse;
import com.coldchain.tracker.service.TrackerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trackers")
public class TrackerController {

    private final TrackerService trackerService;

    public TrackerController(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @PostMapping
    public ResponseEntity<TrackerRegisterResponse> register(@Valid @RequestBody TrackerRegisterRequest request) {
        TrackerRegisterResponse response = trackerService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/trackers/" + response.trackerId())).body(response);
    }
}
