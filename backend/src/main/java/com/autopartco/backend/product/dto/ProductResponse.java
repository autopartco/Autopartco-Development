package com.autopartco.backend.product.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        UUID id,
        UUID storeId,
        UUID branchId,
        String branchName,
        String name,
        String partNumber,
        String description,
        BigDecimal price,
        BigDecimal effectivePrice,
        BigDecimal discountAmount,
        String appliedPromotionName,
        Integer quantity,
        UUID manufacturerId,
        UUID categoryId,
        String state,
        Map<String, Object> attributes,
        Set<UUID> compatibilityIds,
        List<ProductImageResponse> images
) {
}