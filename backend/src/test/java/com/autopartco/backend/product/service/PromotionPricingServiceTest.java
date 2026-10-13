package com.autopartco.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.product.entity.Category;
import com.autopartco.backend.product.entity.Inventory;
import com.autopartco.backend.product.entity.Manufacturer;
import com.autopartco.backend.product.entity.Product;
import com.autopartco.backend.product.entity.Promotion;
import com.autopartco.backend.product.entity.PromotionAttributeOperator;
import com.autopartco.backend.product.entity.PromotionDiscountType;
import com.autopartco.backend.product.entity.PromotionScope;
import com.autopartco.backend.product.entity.PromotionTargetType;
import com.autopartco.backend.core.organization.internal.Store;

class PromotionPricingServiceTest {
    private final PromotionPricingService pricingService = new PromotionPricingService();

    @Test
    void appliesOnlyThePromotionWithTheLowestEffectivePrice() {
        Product product = product();
        Inventory inventory = inventory(product, "100.00");
        Promotion tenPercent = promotion(
                PromotionTargetType.ALL, null, PromotionDiscountType.PERCENTAGE,
                "10.00", null, null);
        Promotion fixedFifteen = promotion(
                PromotionTargetType.ALL, null, PromotionDiscountType.FIXED_AMOUNT,
                "15.00", null, null);

        var quote = pricingService.bestPrice(inventory, List.of(tenPercent, fixedFifteen));

        assertEquals(new BigDecimal("85.00"), quote.effectivePrice());
        assertEquals(new BigDecimal("15.00"), quote.discountAmount());
        assertEquals("promotion", quote.promotionName());
    }

    @Test
    void capsFixedDiscountAtThePriceAndMatchesCustomAttributes() {
        Product product = product();
        product.setAttributes(Map.of("fuelType", "Diesel"));
        Inventory inventory = inventory(product, "10.00");
        Promotion promotion = promotion(
                PromotionTargetType.ATTRIBUTE, null, PromotionDiscountType.FIXED_AMOUNT,
                "20.00", "fuelType", "diesel");

        var quote = pricingService.bestPrice(inventory, List.of(promotion));

        assertEquals(new BigDecimal("0.00"), quote.effectivePrice());
        assertEquals(new BigDecimal("10.00"), quote.discountAmount());
        assertEquals("promotion", quote.promotionName());
    }

    @Test
    void doesNotApplyStorePromotionToAnotherStore() {
        Product product = product();
        Inventory inventory = inventory(product, "100.00");
        Store otherStore = new Store();
        otherStore.setId(UUID.randomUUID());
        Promotion promotion = Promotion.builder()
                .scope(PromotionScope.STORE)
                .store(otherStore)
                .name("promotion")
                .targetType(PromotionTargetType.ALL)
                .discountType(PromotionDiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("50.00"))
                .build();

        var quote = pricingService.bestPrice(inventory, List.of(promotion));

        assertEquals(new BigDecimal("100.00"), quote.effectivePrice());
        assertEquals(BigDecimal.ZERO.setScale(2), quote.discountAmount());
        assertNull(quote.promotionName());
    }

    private Product product() {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        Category category = new Category();
        category.setId(UUID.randomUUID());
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(UUID.randomUUID());
        State state = new State();
        state.setCode("ACTIVE");

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setStore(store);
        product.setCategory(category);
        product.setManufacturer(manufacturer);
        product.setState(state);
        product.setAttributes(Map.of());
        return product;
    }

    private Inventory inventory(Product product, String price) {
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setPrice(new BigDecimal(price));
        return inventory;
    }

    private Promotion promotion(
            PromotionTargetType targetType,
            UUID targetId,
            PromotionDiscountType discountType,
            String discountValue,
            String attributeKey,
            String attributeValue
    ) {
        return Promotion.builder()
                .scope(PromotionScope.PLATFORM)
                .name("promotion")
                .targetType(targetType)
                .targetId(targetId)
                .attributeKey(attributeKey)
                .attributeValue(attributeValue)
                .attributeOperator(PromotionAttributeOperator.EQUALS)
                .discountType(discountType)
                .discountValue(new BigDecimal(discountValue))
                .build();
    }
}
