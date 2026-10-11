package com.autopartco.backend.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.product.entity.PromotionAttributeOperator;
import com.autopartco.backend.product.entity.PromotionDiscountType;
import com.autopartco.backend.product.entity.PromotionScope;
import com.autopartco.backend.product.entity.PromotionTargetType;

public record PromotionResponse(
        UUID id,
        PromotionScope scope,
        UUID storeId,
        String name,
        PromotionTargetType targetType,
        UUID targetId,
        String attributeKey,
        String attributeValue,
        PromotionAttributeOperator attributeOperator,
        PromotionDiscountType discountType,
        BigDecimal discountValue,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean enabled,
        boolean currentlyActive
) {
}
