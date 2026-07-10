package com.coldchain.shipment.service;

import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.auth.repository.AppUserRepository;
import com.coldchain.shipment.domain.ShipmentStatus;
import com.coldchain.shipment.dto.AdminOverviewResponse;
import com.coldchain.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminOverviewService {

    private final AppUserRepository appUserRepository;
    private final ShipmentRepository shipmentRepository;

    public AdminOverviewService(AppUserRepository appUserRepository, ShipmentRepository shipmentRepository) {
        this.appUserRepository = appUserRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public AdminOverviewResponse getOverview() {
        long shipperCount = appUserRepository.countByRole(AppUserRole.SHIPPER);
        long activeTrackerCount = shipmentRepository.countByStatusNot(ShipmentStatus.DELIVERED);
        return new AdminOverviewResponse(shipperCount, activeTrackerCount);
    }
}
