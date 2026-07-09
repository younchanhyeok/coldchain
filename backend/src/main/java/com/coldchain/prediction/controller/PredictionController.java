package com.coldchain.prediction.controller;

import com.coldchain.prediction.dto.PredictionResponse;
import com.coldchain.prediction.service.PredictionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PredictionController {

    private final PredictionQueryService predictionQueryService;

    public PredictionController(PredictionQueryService predictionQueryService) {
        this.predictionQueryService = predictionQueryService;
    }

    @GetMapping("/api/v1/trackers/{trackerId}/prediction")
    public PredictionResponse get(@PathVariable String trackerId) {
        return predictionQueryService.getCurrent(trackerId);
    }
}
