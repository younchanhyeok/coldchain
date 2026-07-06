package com.coldchain.shipment.domain;

public enum ShipmentStatus {
    READY,
    IN_TRANSIT,
    DELIVERED;

    public boolean canTransitionTo(ShipmentStatus next) {
        return switch (this) {
            case READY -> next == IN_TRANSIT;
            case IN_TRANSIT -> next == DELIVERED;
            case DELIVERED -> false;
        };
    }
}
