package com.coldchain.shipment.repository;

import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    boolean existsByTrackerIdAndStatusNot(String trackerId, ShipmentStatus status);
}
