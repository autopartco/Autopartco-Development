package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        PromotionAudience audience,
        /** Categoría de servicio en {@code SERVICE_CATEGORY}; nula en los demás tipos. */
        String targetKey,
        /** Fin de la vigencia; puede ser nulo en reglas armadas a mano. */
        OffsetDateTime endsAt
) {
    public PromotionRule {
        audience = audience == null ? PromotionAudience.ALL : audience;
    }

    /** Regla de producto (sin {@code targetKey} ni {@code endsAt}). */
    public PromotionRule(UUID promotionId, PromotionScope scope, UUID storeId, String name,
            PromotionTargetType targetType, UUID targetId, String attributeKey, String attributeValue,
            PromotionAttributeOperator attributeOperator, PromotionDiscountType discountType, BigDecimal discountValue,
            PromotionAudience audience) {
        this(promotionId, scope, storeId, name, targetType, targetId, attributeKey, attributeValue, attributeOperator,
                discountType, discountValue, audience, null, null);
    }
}
