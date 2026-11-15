package com.autopartco.backend.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.pricing.internal.PromotionRepository;

/** Precio «desde» de servicios: qué promociones aplican, cuál gana y la etiqueta. */
class PriceCalculatorServiceTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID OTHER_STORE = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final BigDecimal PRICE = new BigDecimal("60000");
    private static final OffsetDateTime ENDS = OffsetDateTime.parse("2026-12-01T00:00:00Z");

    private final PriceCalculator calculator = new PriceCalculator(mock(PromotionRepository.class));

    @Test
    void fifteenPercentOnSixtyThousandIsFiftyOneThousand() {
        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "ALIGNMENT_BALANCING", PRICE,
                List.of(service("Alineación", PromotionScope.STORE, STORE, SERVICE, "15")));

        assertThat(price.priceFrom()).isEqualByComparingTo("51000");
        assertThat(price.basePriceFrom()).isEqualByComparingTo("60000");
        assertThat(price.promotion().label()).isEqualTo("-15%");
        assertThat(price.promotion().endsAt()).isEqualTo(ENDS);
    }

    @Test
    void storeAndGlobalPromotionsDoNotTouchServices() {
        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "AC", PRICE, List.of(
                rule("Todo el almacén", PromotionScope.STORE, STORE, PromotionTargetType.STORE, STORE, null, "20"),
                rule("Global", PromotionScope.PLATFORM, null, PromotionTargetType.ALL, null, null, "30")));

        assertThat(price.hasPromotion()).isFalse();
        assertThat(price.priceFrom()).isEqualByComparingTo("60000");
    }

    @Test
    void anotherStoresServicePromotionDoesNotApply() {
        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "AC", PRICE,
                List.of(service("Ajena", PromotionScope.STORE, OTHER_STORE, SERVICE, "50")));

        assertThat(price.hasPromotion()).isFalse();
    }

    @Test
    void categoryPromotionsOfPlatformOrTheSameStoreApplyAndTheLowestWins() {
        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "LUBRICATION", PRICE, List.of(
                category("Plataforma", PromotionScope.PLATFORM, null, "LUBRICATION", "10"),
                category("Almacén", PromotionScope.STORE, STORE, "LUBRICATION", "25"),
                category("Otro almacén", PromotionScope.STORE, OTHER_STORE, "LUBRICATION", "60"),
                category("Otra categoría", PromotionScope.PLATFORM, null, "AC", "70")));

        assertThat(price.promotion().name()).isEqualTo("Almacén");
        assertThat(price.priceFrom()).isEqualByComparingTo("45000");
    }

    @Test
    void segmentedAudiencesAreIgnored() {
        PromotionRule segmented = new PromotionRule(UUID.randomUUID(), PromotionScope.STORE, STORE, "Frecuentes",
                PromotionTargetType.SERVICE, SERVICE, null, null, null, PromotionDiscountType.PERCENTAGE,
                new BigDecimal("40"), PromotionAudience.STORE_FREQUENT, null, ENDS);

        assertThat(calculator.servicePrice(SERVICE, STORE, "AC", PRICE, List.of(segmented)).hasPromotion()).isFalse();
    }

    @Test
    void withoutPriceOnlyThePromotionTextIsShown() {
        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "AC", null,
                List.of(service("Sin precio", PromotionScope.STORE, STORE, SERVICE, "15")));

        assertThat(price.priceFrom()).isNull();
        assertThat(price.basePriceFrom()).isNull();
        assertThat(price.promotion().label()).isEqualTo("-15%");
    }

    @Test
    void fixedAmountLabelUsesPesos() {
        PromotionRule fixed = new PromotionRule(UUID.randomUUID(), PromotionScope.STORE, STORE, "Fijo",
                PromotionTargetType.SERVICE, SERVICE, null, null, null, PromotionDiscountType.FIXED_AMOUNT,
                new BigDecimal("9000.00"), PromotionAudience.ALL, null, ENDS);

        ServicePrice price = calculator.servicePrice(SERVICE, STORE, "AC", PRICE, List.of(fixed));

        assertThat(price.promotion().label()).isEqualTo("-$9.000");
        assertThat(price.priceFrom()).isEqualByComparingTo("51000");
    }

    @Test
    void servicePromotionsNeverChangeProducts() {
        PricingSubject product = new PricingSubject(PRICE, SERVICE, STORE, UUID.randomUUID(), UUID.randomUUID(),
                "Filtro", "", "SKU", "PN", "ACTIVE", Map.of(), Set.of());

        PriceQuote quote = calculator.bestPrice(product, List.of(
                service("Servicio", PromotionScope.STORE, STORE, SERVICE, "50"),
                category("Categoría", PromotionScope.PLATFORM, null, "AC", "50")));

        assertThat(quote.promotionId()).isNull();
        assertThat(quote.effectivePrice()).isEqualByComparingTo("60000");
    }

    private static PromotionRule service(String name, PromotionScope scope, UUID storeId, UUID serviceId,
            String percent) {
        return rule(name, scope, storeId, PromotionTargetType.SERVICE, serviceId, null, percent);
    }

    private static PromotionRule category(String name, PromotionScope scope, UUID storeId, String key,
            String percent) {
        return rule(name, scope, storeId, PromotionTargetType.SERVICE_CATEGORY, null, key, percent);
    }

    private static PromotionRule rule(String name, PromotionScope scope, UUID storeId, PromotionTargetType type,
            UUID targetId, String key, String percent) {
        return new PromotionRule(UUID.randomUUID(), scope, storeId, name, type, targetId, null, null, null,
                PromotionDiscountType.PERCENTAGE, new BigDecimal(percent), PromotionAudience.ALL, key, ENDS);
    }
}
