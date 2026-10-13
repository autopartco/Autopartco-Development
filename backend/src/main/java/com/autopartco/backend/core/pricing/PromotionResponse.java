package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


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
