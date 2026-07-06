package com.coldchain.shipment.service;

import com.coldchain.common.DevShipperProvider;
import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.DuplicateResourceException;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.common.error.SemanticInvalidException;
import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.ShipmentCreateRequest;
import com.coldchain.shipment.dto.ShipmentResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final TrackerRepository trackerRepository;
    private final DevShipperProvider devShipperProvider;

    public ShipmentService(ShipmentRepository shipmentRepository, TrackerRepository trackerRepository,
            DevShipperProvider devShipperProvider) {
        this.shipmentRepository = shipmentRepository;
        this.trackerRepository = trackerRepository;
        this.devShipperProvider = devShipperProvider;
    }

    @Transactional
    public ShipmentResponse create(ShipmentCreateRequest request) {
        if (!trackerRepository.existsById(request.trackerId())) {
            throw new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + request.trackerId());
        }
        if (shipmentRepository.existsByTrackerIdAndStatusNot(request.trackerId(), ShipmentStatus.DELIVERED)) {
            throw new DuplicateResourceException("이미 진행 중인 배송이 있는 트래커입니다: " + request.trackerId());
        }

        Shipment shipment = new Shipment(
                devShipperProvider.shipperId(),
                request.trackerId(),
                request.productName(),
                GeoPoints.of(request.origin().lat(), request.origin().lon()),
                request.origin().name(),
                GeoPoints.of(request.destination().lat(), request.destination().lon()),
                request.destination().name(),
                request.consignee() != null ? request.consignee().name() : null,
                request.consignee() != null ? request.consignee().contact() : null,
                request.driverContact());

        shipmentRepository.save(shipment);
        return new ShipmentResponse(shipment.getId(), shipment.getStatus());
    }

    @Transactional
    public ShipmentResponse updateStatus(Long shipmentId, ShipmentStatus nextStatus) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("배송을 찾을 수 없습니다: " + shipmentId));

        try {
            shipment.transitionTo(nextStatus);
        } catch (IllegalStateException e) {
            throw new SemanticInvalidException(e.getMessage());
        }

        return new ShipmentResponse(shipment.getId(), shipment.getStatus());
    }
}
