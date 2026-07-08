package com.coldchain.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false)
    private Long shipperId;

    @Column(name = "tracker_id", nullable = false)
    private String trackerId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "origin_position")
    private Point originPosition;

    @Column(name = "origin_name")
    private String originName;

    @Column(name = "destination_position")
    private Point destinationPosition;

    @Column(name = "destination_name")
    private String destinationName;

    @Column(name = "consignee_name")
    private String consigneeName;

    @Column(name = "consignee_contact")
    private String consigneeContact;

    @Column(name = "driver_contact")
    private String driverContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "in_transit_at")
    private Instant inTransitAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Shipment() {
    }

    public Shipment(Long shipperId, String trackerId, String productName,
            Point originPosition, String originName,
            Point destinationPosition, String destinationName,
            String consigneeName, String consigneeContact, String driverContact) {
        this.shipperId = shipperId;
        this.trackerId = trackerId;
        this.productName = productName;
        this.originPosition = originPosition;
        this.originName = originName;
        this.destinationPosition = destinationPosition;
        this.destinationName = destinationName;
        this.consigneeName = consigneeName;
        this.consigneeContact = consigneeContact;
        this.driverContact = driverContact;
        this.status = ShipmentStatus.READY;
        this.createdAt = Instant.now();
    }

    public void transitionTo(ShipmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않는 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
        if (next == ShipmentStatus.IN_TRANSIT) {
            this.inTransitAt = Instant.now();
        } else if (next == ShipmentStatus.DELIVERED) {
            this.deliveredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getShipperId() {
        return shipperId;
    }

    public String getTrackerId() {
        return trackerId;
    }

    public String getProductName() {
        return productName;
    }

    public Point getOriginPosition() {
        return originPosition;
    }

    public String getOriginName() {
        return originName;
    }

    public Point getDestinationPosition() {
        return destinationPosition;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getConsigneeName() {
        return consigneeName;
    }

    public String getConsigneeContact() {
        return consigneeContact;
    }

    public String getDriverContact() {
        return driverContact;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getInTransitAt() {
        return inTransitAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
