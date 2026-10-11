package com.autopartco.backend.product.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.autopartco.backend.product.entity.Inventory;
import com.autopartco.backend.product.entity.Product;
import com.autopartco.backend.product.entity.Promotion;
import com.autopartco.backend.product.entity.PromotionAttributeOperator;
import com.autopartco.backend.product.entity.PromotionDiscountType;
import com.autopartco.backend.product.entity.PromotionScope;
import com.autopartco.backend.product.entity.PromotionTargetType;

@Service
public class PromotionPricingService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public PriceQuote bestPrice(Inventory inventory, List<Promotion> promotions) {
        BigDecimal originalPrice = inventory.getPrice().setScale(2, RoundingMode.HALF_UP);
        return promotions.stream()
                .filter(promotion -> appliesTo(promotion, inventory.getProduct()))
                .map(promotion -> quote(originalPrice, promotion))
                .min((first, second) -> {
                    int priceComparison = first.effectivePrice().compareTo(second.effectivePrice());
                    if (priceComparison != 0) {
                        return priceComparison;
                    }
                    return first.promotionName().compareToIgnoreCase(second.promotionName());
                })
                .orElse(new PriceQuote(originalPrice, ZERO, null));
    }

    private PriceQuote quote(BigDecimal originalPrice, Promotion promotion) {
        BigDecimal discount = switch (promotion.getDiscountType()) {
            case PERCENTAGE -> originalPrice.multiply(promotion.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promotion.getDiscountValue();
        };
        BigDecimal effectivePrice = originalPrice.subtract(discount).max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceQuote(
                effectivePrice,
                originalPrice.subtract(effectivePrice),
                promotion.getName()
        );
    }

    private boolean appliesTo(Promotion promotion, Product product) {
        if (promotion.getScope() == PromotionScope.STORE
                && !promotion.getStore().getId().equals(product.getStore().getId())) {
            return false;
        }
        return switch (promotion.getTargetType()) {
            case ALL -> true;
            case STORE -> product.getStore().getId().equals(promotion.getTargetId());
            case PRODUCT -> product.getId().equals(promotion.getTargetId());
            case CATEGORY -> product.getCategory().getId().equals(promotion.getTargetId());
            case MANUFACTURER -> product.getManufacturer().getId().equals(promotion.getTargetId());
            case ATTRIBUTE -> matchesAttribute(promotion, product);
        };
    }

    private boolean matchesAttribute(Promotion promotion, Product product) {
        Object value = switch (promotion.getAttributeKey()) {
            case "name" -> product.getName();
            case "description" -> product.getDescription();
            case "sku" -> product.getSku();
            case "partNumber" -> product.getPartNumber();
            case "state" -> product.getState().getCode();
            case "categoryId" -> product.getCategory().getId();
            case "manufacturerId", "brandId" -> product.getManufacturer().getId();
            case "storeId" -> product.getStore().getId();
            case "compatibilityIds" -> product.getCompatibilities().stream()
                    .map(com.autopartco.backend.product.entity.Compatibility::getId)
                    .toList();
            default -> customAttributeValue(promotion.getAttributeKey(), product.getAttributes());
        };
        if (value == null) {
            return false;
        }
        return matchesValue(value, promotion.getAttributeValue(), promotion.getAttributeOperator());
    }

    private Object customAttributeValue(String key, java.util.Map<String, Object> attributes) {
        if (!key.startsWith("attributes.")) {
            return attributes.get(key);
        }
        Object value = attributes;
        for (String part : key.substring("attributes.".length()).split("\\.")) {
            if (!(value instanceof java.util.Map<?, ?> map)) {
                return null;
            }
            value = map.get(part);
        }
        return value;
    }

    private boolean matchesValue(
            Object productValue,
            String expectedValue,
            PromotionAttributeOperator operator
    ) {
        PromotionAttributeOperator effectiveOperator = operator == null
                ? PromotionAttributeOperator.EQUALS
                : operator;
        if (productValue instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> matchesValue(value, expectedValue, effectiveOperator));
        }
        String value = productValue instanceof java.util.UUID
                ? productValue.toString()
                : String.valueOf(productValue);
        return switch (effectiveOperator) {
            case EQUALS -> value.equalsIgnoreCase(expectedValue);
            case CONTAINS -> value.toLowerCase(java.util.Locale.ROOT)
                    .contains(expectedValue.toLowerCase(java.util.Locale.ROOT));
        };
    }

    public record PriceQuote(BigDecimal effectivePrice, BigDecimal discountAmount, String promotionName) {
    }
}
