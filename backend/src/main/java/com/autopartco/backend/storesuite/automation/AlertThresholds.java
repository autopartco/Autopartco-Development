package com.autopartco.backend.storesuite.automation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.autopartco.backend.core.analytics.AlertRules;
import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Umbrales conocidos por tipo: {@code SALES_DROP {"ratio": 0,10-0,95}}, {@code STOCKOUT_SOON {"days": 1-30}} y
 * {@code DEAD_STOCK {"days": 30-365}}. {@code STOCKOUT} y {@code CUSTOMER_AT_RISK} no tienen umbral. Cualquier otra
 * clave o valor: 400 {@code INVALID_ALERT_THRESHOLD}.
 */
final class AlertThresholds {
    static final String CODE = "INVALID_ALERT_THRESHOLD";
    static final BigDecimal MIN_RATIO = new BigDecimal("0.10");
    static final BigDecimal MAX_RATIO = new BigDecimal("0.95");

    private AlertThresholds() {
    }

    /** Devuelve el umbral normalizado ({@code null} si viene vacío) o lanza 400. */
    static Map<String, Object> validate(AlertRules.Type type, Map<String, Object> threshold) {
        if (threshold == null || threshold.isEmpty()) {
            return null;
        }
        return switch (type) {
            case SALES_DROP -> single(threshold, "ratio", "La razón de caída de ventas debe estar entre 0,10 y 0,95.",
                    value -> value.compareTo(MIN_RATIO) >= 0 && value.compareTo(MAX_RATIO) <= 0, false);
            case STOCKOUT_SOON -> single(threshold, "days", "Los días de cobertura deben ser un entero entre 1 y 30.",
                    value -> value.compareTo(BigDecimal.ONE) >= 0 && value.compareTo(BigDecimal.valueOf(30)) <= 0,
                    true);
            case DEAD_STOCK -> single(threshold, "days", "Los días sin ventas deben ser un entero entre 30 y 365.",
                    value -> value.compareTo(BigDecimal.valueOf(30)) >= 0
                            && value.compareTo(BigDecimal.valueOf(365)) <= 0, true);
            case STOCKOUT, CUSTOMER_AT_RISK -> throw invalid("Este tipo de alerta no tiene umbral configurable.");
        };
    }

    private static Map<String, Object> single(Map<String, Object> threshold, String key, String message,
                                              java.util.function.Predicate<BigDecimal> inRange, boolean integer) {
        if (threshold.size() != 1 || !threshold.containsKey(key)) {
            throw invalid(message);
        }
        Object raw = threshold.get(key);
        if (!(raw instanceof Number number)) {
            throw invalid(message);
        }
        BigDecimal value;
        try {
            value = new BigDecimal(number.toString());
        } catch (NumberFormatException notFinite) {
            throw invalid(message);
        }
        if (!inRange.test(value) || (integer && value.stripTrailingZeros().scale() > 0)) {
            throw invalid(message);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put(key, integer ? (Object) value.intValueExact() : value.stripTrailingZeros());
        return normalized;
    }

    /** Umbrales efectivos con las preferencias guardadas; un valor ilegible en BD vuelve al por defecto. */
    static AlertRules.Thresholds effective(Map<AlertRules.Type, AlertPreferenceResponse> preferences) {
        AlertRules.Thresholds defaults = AlertRules.Thresholds.defaults();
        BigDecimal ratio = number(preferences, AlertRules.Type.SALES_DROP, "ratio");
        BigDecimal coverage = number(preferences, AlertRules.Type.STOCKOUT_SOON, "days");
        BigDecimal dead = number(preferences, AlertRules.Type.DEAD_STOCK, "days");
        return new AlertRules.Thresholds(
                ratio != null && ratio.compareTo(MIN_RATIO) >= 0 && ratio.compareTo(MAX_RATIO) <= 0
                        ? ratio : defaults.salesDropRatio(),
                coverage != null && coverage.signum() > 0 ? coverage.doubleValue() : defaults.coverageDays(),
                dead != null && dead.signum() > 0 ? dead.intValue() : defaults.deadStockDays());
    }

    private static BigDecimal number(Map<AlertRules.Type, AlertPreferenceResponse> preferences,
                                     AlertRules.Type type, String key) {
        AlertPreferenceResponse preference = preferences.get(type);
        if (preference == null || preference.threshold() == null
                || !(preference.threshold().get(key) instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException notFinite) {
            return null;
        }
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException(CODE, message);
    }
}
