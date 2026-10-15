package com.autopartco.backend.core.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado de un descuento de stock aplicado: listado afectado, stock que quedó y precio base vigente en el momento
 * del {@code UPDATE} (permite al llamador detectar un cambio de precio durante la compra).
 */
public record StockDecrement(UUID inventoryId, int newStock, BigDecimal price) {
}
