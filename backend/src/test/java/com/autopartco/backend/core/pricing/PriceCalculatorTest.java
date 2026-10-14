package com.autopartco.backend.core.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.pricing.internal.PromotionRepository;

class PriceCalculatorTest {
    private final PriceCalculator pricingService = new PriceCalculator(mock(PromotionRepository.class));

    @Test
    void appliesOnlyThePromotionWithTheLowestEffectivePrice() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule tenPercent = promotion(
                PromotionTargetType.ALL, null, PromotionDiscountType.PERCENTAGE,
                "10.00", null, null);
        PromotionRule fixedFifteen = promotion(
                PromotionTargetType.ALL, null, PromotionDiscountType.FIXED_AMOUNT,
                "15.00", null, null);

        var quote = pricingService.bestPrice(subject, List.of(tenPercent, fixedFifteen));

        assertEquals(new BigDecimal("85.00"), quote.effectivePrice());
        assertEquals(new BigDecimal("15.00"), quote.discountAmount());
        assertEquals("promotion", quote.promotionName());
    }

    @Test
    void capsFixedDiscountAtThePriceAndMatchesCustomAttributes() {
        PricingSubject subject = subject("10.00", Map.of("fuelType", "Diesel"));
        PromotionRule promotion = promotion(
                PromotionTargetType.ATTRIBUTE, null, PromotionDiscountType.FIXED_AMOUNT,
                "20.00", "fuelType", "diesel");

        var quote = pricingService.bestPrice(subject, List.of(promotion));

        assertEquals(new BigDecimal("0.00"), quote.effectivePrice());
        assertEquals(new BigDecimal("10.00"), quote.discountAmount());
        assertEquals("promotion", quote.promotionName());
    }

    @Test
    void doesNotApplyStorePromotionToAnotherStore() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule promotion = new PromotionRule(
                PromotionScope.STORE,
                UUID.randomUUID(),
                "promotion",
                PromotionTargetType.ALL,
                null,
                null,
                null,
                null,
                PromotionDiscountType.PERCENTAGE,
                new BigDecimal("50.00")
        );

        var quote = pricingService.bestPrice(subject, List.of(promotion));

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertEquals(BigDecimal.ZERO.setScale(2), quote.discountAmount());
        assertNull(quote.promotionName());
    }

    private PricingSubject subject(String price, Map<String, Object> attributes) {
        return new PricingSubject(
                new BigDecimal(price),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                "ACTIVE",
                attributes,
                Set.of()
        );
    }

    private PromotionRule promotion(
            PromotionTargetType targetType,
            UUID targetId,
            PromotionDiscountType discountType,
            String discountValue,
            String attributeKey,
            String attributeValue
    ) {
        return new PromotionRule(
                PromotionScope.PLATFORM,
                null,
                "promotion",
                targetType,
                targetId,
                attributeKey,
                attributeValue,
                PromotionAttributeOperator.EQUALS,
                discountType,
                new BigDecimal(discountValue)
        );
    }
}
