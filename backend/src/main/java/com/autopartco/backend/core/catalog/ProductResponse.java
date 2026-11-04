package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listado de un producto en una sucursal (panel del almacén y marketplace). {@code manufacturerName} se muestra
 * aunque el fabricante esté pendiente de aprobación ({@code manufacturerApproved=false}); no se exponen más datos
 * del fabricante pendiente.
 */
public record ProductResponse(
        UUID id,
        UUID storeId,
        UUID branchId,
        String branchName,
        String name,
        String sku,
        String partNumber,
        String description,
        BigDecimal price,
        BigDecimal effectivePrice,
        BigDecimal discountAmount,
        String appliedPromotionName,
        Integer quantity,
        UUID manufacturerId,
        String manufacturerName,
        boolean manufacturerApproved,
        UUID categoryId,
        String state,
        boolean universal,
        Map<String, Object> attributes,
        List<VehicleRuleResponse> vehicleRules,
        List<ProductImageResponse> images
) {
}
