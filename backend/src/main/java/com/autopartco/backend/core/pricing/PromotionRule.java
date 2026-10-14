package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/** Regla de una promoción vigente, tal como la evalúa {@link PriceCalculator}. */
public record PromotionRule(
        PromotionScope scope,
        UUID storeId,
        String name,
        PromotionTargetType targetType,
        UUID targetId,
        String attributeKey,
        String attributeValue,
        PromotionAttributeOperator attributeOperator,
        PromotionDiscountType discountType,
        BigDecimal discountValue
) {
}
