package com.autopartco.backend.core.inventory;

import java.util.UUID;

/** Unidades que vuelven a un listado de inventario (p. ej. al vencer la reserva de un pedido sin pagar). */
public record StockReturn(UUID inventoryId, int units) {
    public StockReturn {
        if (inventoryId == null) {
            throw new IllegalArgumentException("inventoryId is required");
        }
        if (units < 1) {
            throw new IllegalArgumentException("units must be >= 1");
        }
    }
}
