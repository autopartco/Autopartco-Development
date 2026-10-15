package com.autopartco.backend.core.catalog;

import java.util.UUID;

/** Identifica un listado: un producto en una sucursal. */
public record ListingKey(UUID productId, UUID branchId) {
}
