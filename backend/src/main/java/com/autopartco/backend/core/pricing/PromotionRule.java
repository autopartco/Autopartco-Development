package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/** Regla de una promoción vigente, tal como la evalúa {@link PriceCalculator}. Audiencia nula = {@code ALL}. */
public record PromotionRule(
        UUID promotionId,
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
        PromotionAudience audience
) {
    public PromotionRule {
        audience = audience == null ? PromotionAudience.ALL : audience;
    }
}
