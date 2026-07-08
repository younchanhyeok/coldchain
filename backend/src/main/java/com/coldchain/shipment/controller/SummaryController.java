package com.coldchain.shipment.controller;

import com.coldchain.shipment.dto.SummaryResponse;
import com.coldchain.shipment.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/api/v1/summary")
    public SummaryResponse getSummary() {
        return summaryService.getSummary();
    }
}
