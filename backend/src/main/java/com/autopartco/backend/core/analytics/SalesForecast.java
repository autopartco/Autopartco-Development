package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pronóstico de ingresos semanales y del próximo mes calendario. Con menos de {@code weeksRequired} semanas de
 * historial: {@code INSUFFICIENT_HISTORY}, {@code method} y {@code nextMonth} nulos, {@code weekly} solo con valores
 * reales y {@code topProducts} vacío.
 */
public record SalesForecast(
        String status,
        int weeksAvailable,
        int weeksRequired,
        String method,
        NextMonth nextMonth,
        List<WeekPoint> weekly,
        List<ProductForecast> topProducts
) {
    public static final String OK = "OK";
    public static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";

    /** {@code month} en formato {@code YYYY-MM}. */
    public record NextMonth(String month, BigDecimal expected, BigDecimal low, BigDecimal high) {
    }

    /** Semana (lunes): {@code actual} en semanas pasadas; {@code forecast}, {@code low} y {@code high} en las futuras. */
    public record WeekPoint(LocalDate weekStart, BigDecimal actual, BigDecimal forecast, BigDecimal low,
                            BigDecimal high) {
    }

    public record ProductForecast(UUID productId, String name, BigDecimal expectedUnits,
                                  BigDecimal expectedRevenue) {
    }
}
