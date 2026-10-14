package com.autopartco.backend.core.order;

import java.util.UUID;

/** Pedido y persona dueños de un pago con una referencia de proveedor dada. */
public record PaymentReferenceOwner(UUID orderId, UUID personId) {
}
