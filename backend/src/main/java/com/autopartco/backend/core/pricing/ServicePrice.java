package com.autopartco.backend.core.pricing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Precio «desde» de un servicio ({@link PriceCalculator#servicePrice}). {@code priceFrom} con la promoción aplicada y
 * {@code basePriceFrom} el del servicio; ambos nulos si el servicio no publica precio. {@code promotion} es nula si
 * ninguna aplica.
 */
public record ServicePrice(BigDecimal priceFrom, BigDecimal basePriceFrom, Applied promotion) {

    public boolean hasPromotion() {
        return promotion != null;
    }

    /** {@code label}: {@code -15%} o {@code -$9.000}. */
    public record Applied(UUID id, String name, String label, OffsetDateTime endsAt) {
    }
}
