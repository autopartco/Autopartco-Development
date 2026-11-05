package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Rendimiento de los productos del almacén en los últimos {@code windowDays} días: clase ABC por ingresos, clase
 * XYZ por variabilidad semanal, cobertura de stock, stock mínimo vigente y sugerido, y acción recomendada.
 */
public record ProductReport(int windowDays, List<Product> products) {
    public record Product(
            UUID productId,
            String name,
            String sku,
            BigDecimal revenue,
            long units,
            String abcClass,
            String xyzClass,
            long stock,
            BigDecimal avgDailyUnits,
            BigDecimal coverageDays,
            int currentMinStock,
            Integer suggestedMinStock,
            ProductAction action
    ) {
    }

    public enum ProductAction {
        PROTECT_STOCK,
        NO_MOVEMENT,
        LIQUIDATE,
        REVIEW_PRICE,
        MAINTAIN
    }
}
