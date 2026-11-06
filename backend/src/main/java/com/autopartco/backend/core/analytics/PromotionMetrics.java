package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/** Cálculo puro de los resultados de una promoción a partir de los conteos que entregan las consultas. */
final class PromotionMetrics {
    /** Días previos al inicio que forman la línea base. */
    static final int BASELINE_DAYS = 28;
    /** Mínimo de personas expuestas para comparar contra el grupo de control. */
    static final long MIN_EXPOSED = 30;

    private PromotionMetrics() {
    }

    /**
     * Conteos crudos. {@code exposedBuyers}: expuestos que compraron en el ámbito durante la vigencia;
     * {@code controlSize}/{@code controlBuyers}: compradores del ámbito de los 365 días previos que no fueron expuestos
     * y cuántos de ellos compraron en la vigencia.
     */
    record Raw(
            long exposed,
            long exposedBuyers,
            long controlSize,
            long controlBuyers,
            long buyers,
            long orders,
            BigDecimal revenue,
            long unitsSold,
            BigDecimal baselineRevenue,
            long baselineOrders
    ) {
    }

    static PromotionResult compute(Raw raw, boolean audienceAll, OffsetDateTime endsAt, OffsetDateTime now) {
        PromotionResult.Baseline baseline = new PromotionResult.Baseline(
                Analytics.money(perDay(raw.baselineRevenue())),
                perDay(BigDecimal.valueOf(raw.baselineOrders())).setScale(2, RoundingMode.HALF_UP));
        long exposures = audienceAll ? 0 : raw.exposed();
        BigDecimal exposedConversion = audienceAll ? null : fraction(raw.exposedBuyers(), exposures);
        BigDecimal controlConversion = audienceAll ? null : fraction(raw.controlBuyers(), raw.controlSize());
        boolean enough = !audienceAll && exposures >= MIN_EXPOSED;
        BigDecimal lift = enough ? lift(exposedConversion, controlConversion) : null;
        String status;
        if (!enough) {
            status = PromotionResult.INSUFFICIENT_DATA;
        } else if (!endsAt.isAfter(now)) {
            status = PromotionResult.FINISHED;
        } else {
            status = PromotionResult.RUNNING;
        }
        return new PromotionResult(exposures, raw.buyers(), raw.orders(), Analytics.money(raw.revenue()),
                raw.unitsSold(), baseline, lift, controlConversion, exposedConversion, status);
    }

    /** {@code exposed / control − 1}; {@code null} si falta alguno o el control es 0. */
    static BigDecimal lift(BigDecimal exposedConversion, BigDecimal controlConversion) {
        if (exposedConversion == null || controlConversion == null || controlConversion.signum() == 0) {
            return null;
        }
        return exposedConversion.divide(controlConversion, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
    }

    /** {@code part / total} con 4 decimales; {@code null} si el total es 0. */
    static BigDecimal fraction(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal perDay(BigDecimal total) {
        return Analytics.nonNull(total).divide(BigDecimal.valueOf(BASELINE_DAYS), 4, RoundingMode.HALF_UP);
    }
}
