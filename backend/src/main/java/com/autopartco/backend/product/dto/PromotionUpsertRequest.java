package com.autopartco.backend.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autopartco.backend.product.entity.PromotionAttributeOperator;
import com.autopartco.backend.product.entity.PromotionDiscountType;
import com.autopartco.backend.product.entity.PromotionTargetType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PromotionUpsertRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull PromotionTargetType targetType,
        UUID targetId,
        @Size(max = 100) String attributeKey,
        @Size(max = 255) String attributeValue,
        PromotionAttributeOperator attributeOperator,
        @NotNull PromotionDiscountType discountType,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        Boolean enabled
) {
    @AssertTrue(message = "La fecha de finalización debe ser posterior a la fecha de inicio.")
    public boolean hasValidDateRange() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @AssertTrue(message = "Los descuentos porcentuales deben estar entre 0 y 100.")
    public boolean hasValidPercentage() {
        return discountType != PromotionDiscountType.PERCENTAGE
                || discountValue == null
                || (discountValue.signum() > 0 && discountValue.compareTo(new BigDecimal("100")) <= 0);
    }
}
