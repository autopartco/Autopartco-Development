package com.autopartco.backend.shared.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio: una venta pasó a {@code DELIVERED}. Lo publica {@code core.order} dentro de la transacción del
 * cambio de estado; los oyentes lo procesan tras el commit ({@code marketplace.reviews} invita al comprador a calificar).
 * Un pedido con varias ventas lo recibe una vez por venta entregada; quien lo escucha decide si ya actuó.
 *
 * @param saleId  venta entregada
 * @param orderId pedido al que pertenece
 */
public record SaleDelivered(UUID saleId, UUID orderId) {
    public SaleDelivered {
        Objects.requireNonNull(saleId, "saleId");
        Objects.requireNonNull(orderId, "orderId");
    }
}
