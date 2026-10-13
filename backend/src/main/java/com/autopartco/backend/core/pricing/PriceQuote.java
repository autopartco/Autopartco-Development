package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;

/** Precio efectivo, descuento aplicado y nombre de la promoción ganadora ({@code null} si ninguna aplica). */
public record PriceQuote(BigDecimal effectivePrice, BigDecimal discountAmount, String promotionName) {
}
