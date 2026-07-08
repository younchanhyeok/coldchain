package com.coldchain.shipment.controller;

import com.coldchain.shipment.dto.ShipmentCreateRequest;
import com.coldchain.shipment.dto.ShipmentListResponse;
import com.coldchain.shipment.dto.ShipmentResponse;
import com.coldchain.shipment.dto.ShipmentStatusUpdateRequest;
import com.coldchain.shipment.service.ShipmentQueryService;
import com.coldchain.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final ShipmentQueryService shipmentQueryService;

    public ShipmentController(ShipmentService shipmentService, ShipmentQueryService shipmentQueryService) {
        this.shipmentService = shipmentService;
        this.shipmentQueryService = shipmentQueryService;
    }

    @GetMapping
    public ShipmentListResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shipmentQueryService.list(page, size);
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody ShipmentCreateRequest request) {
        ShipmentResponse response = shipmentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + response.shipmentId())).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ShipmentResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody ShipmentStatusUpdateRequest request) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, request.status()));
    }
}
