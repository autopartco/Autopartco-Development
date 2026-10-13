package com.autopartco.backend.core.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShipmentServiceTest {
    @Test
    void permitsOnlyForwardShipmentStatusTransitions() {
        assertTrue(ShipmentService.isShipmentTransitionAllowed("PREPARING", "DISPATCHED"));
        assertTrue(ShipmentService.isShipmentTransitionAllowed("IN_TRANSIT", "DELIVERED"));
        assertFalse(ShipmentService.isShipmentTransitionAllowed("PREPARING", "DELIVERED"));
        assertFalse(ShipmentService.isShipmentTransitionAllowed("DELIVERED", "IN_TRANSIT"));
    }
}
