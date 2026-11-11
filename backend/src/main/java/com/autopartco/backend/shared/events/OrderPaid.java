package com.autopartco.backend.shared.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio: un pedido del marketplace quedó pagado (cobro simulado aprobado o pago Wompi aprobado que confirmó
 * el pedido). Lo publica {@code core.order} dentro de su transacción; los oyentes lo procesan tras el commit
 * ({@code core.advertising} atribuye el pedido a los anuncios clicados). Vive en {@code shared} para que nadie dependa
 * del publicador ni del oyente.
 *
 * @param orderId pedido pagado
 */
public record OrderPaid(UUID orderId) {
    public OrderPaid {
        Objects.requireNonNull(orderId, "orderId");
    }
}
