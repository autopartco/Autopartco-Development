package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.pricing.internal.Promotion;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;

import lombok.AllArgsConstructor;

/**
 * Precio efectivo de un listado: aplica la promoción vigente que deja el precio más bajo (empate: nombre, sin
 * distinguir mayúsculas). Redondeo a 2 decimales {@code HALF_UP}; el precio efectivo nunca es negativo.
 */
@Service
@AllArgsConstructor
public class PriceCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final PromotionRepository promotionRepository;

    /** Promociones habilitadas y vigentes en {@code at} ({@code startsAt <= at < endsAt}). */
    @Transactional(readOnly = true)
    public List<PromotionRule> activeRulesAt(OffsetDateTime at) {
        return promotionRepository.findActiveAt(at).stream().map(PriceCalculator::toRule).toList();
    }

    public PriceQuote bestPrice(PricingSubject subject, List<PromotionRule> promotions) {
        BigDecimal originalPrice = subject.price().setScale(2, RoundingMode.HALF_UP);
        return promotions.stream()
                .filter(promotion -> appliesTo(promotion, subject))
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

    private PriceQuote quote(BigDecimal originalPrice, PromotionRule promotion) {
        BigDecimal discount = switch (promotion.discountType()) {
            case PERCENTAGE -> originalPrice.multiply(promotion.discountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promotion.discountValue();
        };
        BigDecimal effectivePrice = originalPrice.subtract(discount).max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceQuote(
                effectivePrice,
                originalPrice.subtract(effectivePrice),
                promotion.name()
        );
    }

    private boolean appliesTo(PromotionRule promotion, PricingSubject subject) {
        if (promotion.scope() == PromotionScope.STORE
                && !promotion.storeId().equals(subject.storeId())) {
            return false;
        }
        return switch (promotion.targetType()) {
            case ALL -> true;
            case STORE -> subject.storeId().equals(promotion.targetId());
            case PRODUCT -> subject.productId().equals(promotion.targetId());
            case CATEGORY -> subject.categoryId().equals(promotion.targetId());
            case MANUFACTURER -> subject.manufacturerId().equals(promotion.targetId());
            case ATTRIBUTE -> matchesAttribute(promotion, subject);
        };
    }

    private boolean matchesAttribute(PromotionRule promotion, PricingSubject subject) {
        Object value = switch (promotion.attributeKey()) {
            case "name" -> subject.name();
            case "description" -> subject.description();
            case "sku" -> subject.sku();
            case "partNumber" -> subject.partNumber();
            case "state" -> subject.stateCode();
            case "categoryId" -> subject.categoryId();
            case "manufacturerId", "brandId" -> subject.manufacturerId();
            case "storeId" -> subject.storeId();
            case "compatibilityIds" -> List.copyOf(subject.compatibilityIds());
            default -> customAttributeValue(promotion.attributeKey(), subject.attributes());
        };
        if (value == null) {
            return false;
        }
        return matchesValue(value, promotion.attributeValue(), promotion.attributeOperator());
    }

    private Object customAttributeValue(String key, Map<String, Object> attributes) {
        if (!key.startsWith("attributes.")) {
            return attributes.get(key);
        }
        Object value = attributes;
        for (String part : key.substring("attributes.".length()).split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) {
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
        String value = productValue instanceof UUID
                ? productValue.toString()
                : String.valueOf(productValue);
        return switch (effectiveOperator) {
            case EQUALS -> value.equalsIgnoreCase(expectedValue);
            case CONTAINS -> value.toLowerCase(Locale.ROOT)
                    .contains(expectedValue.toLowerCase(Locale.ROOT));
        };
    }

    private static PromotionRule toRule(Promotion promotion) {
        return new PromotionRule(
                promotion.getScope(),
                promotion.getStoreId(),
                promotion.getName(),
                promotion.getTargetType(),
                promotion.getTargetId(),
                promotion.getAttributeKey(),
                promotion.getAttributeValue(),
                promotion.getAttributeOperator(),
                promotion.getDiscountType(),
                promotion.getDiscountValue()
        );
    }
}
