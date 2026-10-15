package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Detalle de un producto del almacén con sus listados por sucursal (cualquier estado de la sucursal). */
public record StoreProductDetail(
        UUID id,
        UUID storeId,
        String name,
        String sku,
        String partNumber,
        String description,
        UUID manufacturerId,
        UUID categoryId,
        String state,
        Map<String, Object> attributes,
        Set<UUID> compatibilityIds,
        List<ProductImageResponse> images,
        List<Listing> listings
) {
    /** Producto en una sucursal. {@code appliedPromotionName} es nulo si no hay promoción vigente. */
    public record Listing(
            UUID inventoryId,
            UUID branchId,
            String branchName,
            String branchState,
            BigDecimal price,
            BigDecimal effectivePrice,
            String appliedPromotionName,
            Integer stock
    ) {
    }
}
