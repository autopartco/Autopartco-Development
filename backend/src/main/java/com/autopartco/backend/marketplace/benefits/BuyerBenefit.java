package com.autopartco.backend.marketplace.benefits;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionTargetType;

/**
 * Promoción vigente exclusiva para el comprador autenticado por su segmento. {@code storeId} y {@code storeName} son
 * del almacén dueño; ambos {@code null} en promociones de plataforma.
 */
public record BuyerBenefit(
        UUID promotionId,
        String name,
        PromotionAudience audience,
        UUID storeId,
        String storeName,
        PromotionDiscountType discountType,
        BigDecimal discountValue,
        PromotionTargetType targetType,
        UUID targetId,
        OffsetDateTime endsAt
) {
}
