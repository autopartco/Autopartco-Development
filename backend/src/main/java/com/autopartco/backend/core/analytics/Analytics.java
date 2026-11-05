package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Constantes y utilidades comunes de la analítica comercial. */
public final class Analytics {
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    /** Estados de venta que cuentan como venta realizada. */
    static final String VALID_SALE_STATES = "('CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED')";

    private Analytics() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(ZONE).toOffsetDateTime();
    }

    static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /** {@code numerator / denominator} con 2 decimales; 0 si el denominador es 0. */
    static BigDecimal ratio(BigDecimal numerator, long denominator) {
        if (denominator == 0 || numerator == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return numerator.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
