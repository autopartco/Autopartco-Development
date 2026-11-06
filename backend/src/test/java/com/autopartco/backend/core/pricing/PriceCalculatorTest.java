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
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID OTHER_STORE_ID = UUID.randomUUID();
    private static final UUID PERSON_ID = UUID.randomUUID();

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
        assertEquals(fixedFifteen.promotionId(), quote.promotionId());
        assertEquals(PromotionAudience.ALL, quote.audience());
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
        PromotionRule promotion = storePromotion(OTHER_STORE_ID, "50.00", PromotionAudience.ALL);

        var quote = pricingService.bestPrice(subject, List.of(promotion));

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertEquals(BigDecimal.ZERO.setScale(2), quote.discountAmount());
        assertNull(quote.promotionName());
        assertNull(quote.promotionId());
        assertNull(quote.audience());
    }

    // --- Audiencias ---

    @Test
    void anonymousBuyerOnlyGetsPromotionsForEveryone() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule everyone = storePromotion(STORE_ID, "10.00", PromotionAudience.ALL);
        PromotionRule frequent = storePromotion(STORE_ID, "30.00", PromotionAudience.STORE_FREQUENT);
        PromotionRule platformFrequent = platformPromotion("40.00", PromotionAudience.PLATFORM_FREQUENT);

        var quote = pricingService.bestPrice(subject, List.of(everyone, frequent, platformFrequent),
                BuyerContext.anonymous());
        // Un anónimo con segmentos inventados tampoco recibe audiencias.
        var forged = pricingService.bestPrice(subject, List.of(frequent, platformFrequent),
                new BuyerContext(null, Set.of("FREQUENT"), Map.of(STORE_ID, Set.of("FREQUENT"))));

        assertEquals(new BigDecimal("90.00"), quote.effectivePrice());
        assertEquals(everyone.promotionId(), quote.promotionId());
        assertEquals(PromotionAudience.ALL, quote.audience());
        assertEquals(new BigDecimal("100.00"), forged.effectivePrice());
        assertNull(forged.promotionId());
    }

    @Test
    void twoArgumentBestPriceIsAnonymous() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule frequent = platformPromotion("40.00", PromotionAudience.PLATFORM_FREQUENT);

        var quote = pricingService.bestPrice(subject, List.of(frequent));

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertNull(quote.audience());
    }

    @Test
    void platformFrequentBuyerGetsPlatformFrequentPromotion() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule everyone = storePromotion(STORE_ID, "10.00", PromotionAudience.ALL);
        PromotionRule platformFrequent = platformPromotion("25.00", PromotionAudience.PLATFORM_FREQUENT);
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of("FREQUENT"), Map.of());

        var quote = pricingService.bestPrice(subject, List.of(everyone, platformFrequent), buyer);

        assertEquals(new BigDecimal("75.00"), quote.effectivePrice());
        assertEquals(new BigDecimal("25.00"), quote.discountAmount());
        assertEquals(platformFrequent.promotionId(), quote.promotionId());
        assertEquals(PromotionAudience.PLATFORM_FREQUENT, quote.audience());
    }

    @Test
    void storeFrequentBuyerGetsStoreFrequentPromotionOfThatStore() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule frequent = storePromotion(STORE_ID, "20.00", PromotionAudience.STORE_FREQUENT);
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of(), Map.of(STORE_ID, Set.of("FREQUENT")));

        var quote = pricingService.bestPrice(subject, List.of(frequent), buyer);

        assertEquals(new BigDecimal("80.00"), quote.effectivePrice());
        assertEquals(frequent.promotionId(), quote.promotionId());
        assertEquals(PromotionAudience.STORE_FREQUENT, quote.audience());
    }

    @Test
    void buyerOutsideTheSegmentKeepsTheGeneralPrice() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule everyone = storePromotion(STORE_ID, "10.00", PromotionAudience.ALL);
        PromotionRule atRisk = storePromotion(STORE_ID, "30.00", PromotionAudience.STORE_AT_RISK);
        PromotionRule platformFrequent = platformPromotion("40.00", PromotionAudience.PLATFORM_FREQUENT);
        // Frecuente en el almacén (no «en riesgo») y sin segmento de plataforma.
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of(), Map.of(STORE_ID, Set.of("FREQUENT")));

        var quote = pricingService.bestPrice(subject, List.of(everyone, atRisk, platformFrequent), buyer);

        assertEquals(new BigDecimal("90.00"), quote.effectivePrice());
        assertEquals(everyone.promotionId(), quote.promotionId());
        assertEquals(PromotionAudience.ALL, quote.audience());
    }

    @Test
    void segmentOfAnotherStoreDoesNotUnlockTheAudience() {
        PricingSubject subject = subject("100.00", Map.of());
        // Promoción de plataforma con audiencia de almacén: se evalúa con el almacén del producto.
        PromotionRule frequentPlatformRule = platformPromotion("50.00", PromotionAudience.STORE_FREQUENT);
        PromotionRule frequentHere = storePromotion(STORE_ID, "30.00", PromotionAudience.STORE_FREQUENT);
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of("FREQUENT"),
                Map.of(OTHER_STORE_ID, Set.of("FREQUENT")));

        var quote = pricingService.bestPrice(subject, List.of(frequentPlatformRule, frequentHere), buyer);

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertNull(quote.promotionId());
    }

    @Test
    void audienceOfAnotherStorePromotionNeverAppliesHere() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule otherStoreFrequent = storePromotion(OTHER_STORE_ID, "50.00", PromotionAudience.STORE_FREQUENT);
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of(),
                Map.of(STORE_ID, Set.of("FREQUENT"), OTHER_STORE_ID, Set.of("FREQUENT")));

        var quote = pricingService.bestPrice(subject, List.of(otherStoreFrequent), buyer);

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertNull(quote.promotionId());
    }

    @Test
    void generalPromotionStillWinsWhenCheaperThanTheAudienceOne() {
        PricingSubject subject = subject("100.00", Map.of());
        PromotionRule everyone = storePromotion(STORE_ID, "35.00", PromotionAudience.ALL);
        PromotionRule frequent = storePromotion(STORE_ID, "20.00", PromotionAudience.STORE_FREQUENT);
        BuyerContext buyer = new BuyerContext(PERSON_ID, Set.of(), Map.of(STORE_ID, Set.of("FREQUENT")));

        var quote = pricingService.bestPrice(subject, List.of(everyone, frequent), buyer);

        assertEquals(new BigDecimal("65.00"), quote.effectivePrice());
        assertEquals(PromotionAudience.ALL, quote.audience());
    }

    @Test
    void nullAudienceIsTreatedAsEveryone() {
        PromotionRule rule = new PromotionRule(UUID.randomUUID(), PromotionScope.PLATFORM, null, "p",
                PromotionTargetType.ALL, null, null, null, null, PromotionDiscountType.PERCENTAGE,
                new BigDecimal("10.00"), null);

        assertEquals(PromotionAudience.ALL, rule.audience());
        assertEquals(new BigDecimal("90.00"),
                pricingService.bestPrice(subject("100.00", Map.of()), List.of(rule)).effectivePrice());
    }

    private PricingSubject subject(String price, Map<String, Object> attributes) {
        return new PricingSubject(
                new BigDecimal(price),
                UUID.randomUUID(),
                STORE_ID,
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
                UUID.randomUUID(),
                PromotionScope.PLATFORM,
                null,
                "promotion",
                targetType,
                targetId,
                attributeKey,
                attributeValue,
                PromotionAttributeOperator.EQUALS,
                discountType,
                new BigDecimal(discountValue),
                PromotionAudience.ALL
        );
    }

    private static PromotionRule storePromotion(UUID storeId, String percentage, PromotionAudience audience) {
        return new PromotionRule(UUID.randomUUID(), PromotionScope.STORE, storeId, "store-" + audience,
                PromotionTargetType.STORE, storeId, null, null, null, PromotionDiscountType.PERCENTAGE,
                new BigDecimal(percentage), audience);
    }

    private static PromotionRule platformPromotion(String percentage, PromotionAudience audience) {
        return new PromotionRule(UUID.randomUUID(), PromotionScope.PLATFORM, null, "platform-" + audience,
                PromotionTargetType.ALL, null, null, null, null, PromotionDiscountType.PERCENTAGE,
                new BigDecimal(percentage), audience);
    }
}
