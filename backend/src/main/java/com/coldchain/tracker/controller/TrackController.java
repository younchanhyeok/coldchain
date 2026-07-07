package com.coldchain.tracker.controller;

import com.coldchain.tracker.dto.TrackResponse;
import com.coldchain.tracker.service.TrackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackController {

    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping("/api/v1/trackers/{trackerId}/track")
    public TrackResponse getTrack(@PathVariable String trackerId) {
        return trackService.getTrack(trackerId);
    }
}
