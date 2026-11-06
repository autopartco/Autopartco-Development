package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Precio efectivo, descuento aplicado y promoción ganadora (nombre, id y audiencia; los tres {@code null} si ninguna
 * aplica).
 */
public record PriceQuote(
        BigDecimal effectivePrice,
        BigDecimal discountAmount,
        String promotionName,
        UUID promotionId,
        PromotionAudience audience
) {
}
