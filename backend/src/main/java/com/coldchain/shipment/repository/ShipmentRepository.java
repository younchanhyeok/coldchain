package com.coldchain.shipment.repository;

import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    boolean existsByTrackerIdAndStatusNot(String trackerId, ShipmentStatus status);

    List<Shipment> findByShipperIdAndStatus(Long shipperId, ShipmentStatus status);

    Optional<Shipment> findByTrackerIdAndStatusNot(String trackerId, ShipmentStatus status);
}
