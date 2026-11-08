package com.autopartco.backend.marketplace.reviews;

import java.util.List;
import java.util.UUID;

/** Pedidos entregados del comprador con este almacén que todavía no tienen reseña. */
public record ReviewEligibility(List<UUID> eligibleOrderIds) {
}
