package com.autopartco.backend.core.analytics;

import java.util.Locale;

import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Segmento de un cliente según sus compras de los últimos {@value #WINDOW_DAYS} días y la fecha de su última compra
 * (todo el historial). Reglas, en este orden:
 * <ul>
 *   <li>{@code DORMANT}: última compra hace más de 180 días.</li>
 *   <li>{@code NEW}: 1 compra en la ventana y la primera hace como mucho 30 días.</li>
 *   <li>{@code FREQUENT}: 3 o más compras y la última hace como mucho 60 días.</li>
 *   <li>{@code AT_RISK}: 2 o más compras y la última hace entre 61 y 180 días.</li>
 *   <li>{@code OCCASIONAL}: el resto.</li>
 * </ul>
 */
public enum CustomerSegment {
    NEW("Nuevos"),
    FREQUENT("Frecuentes"),
    AT_RISK("En riesgo"),
    DORMANT("Dormidos"),
    OCCASIONAL("Ocasionales");

    public static final int WINDOW_DAYS = 365;
    static final int NEW_MAX_DAYS = 30;
    static final int FREQUENT_MIN_ORDERS = 3;
    static final int FREQUENT_MAX_DAYS = 60;
    static final int AT_RISK_MIN_ORDERS = 2;
    static final int DORMANT_AFTER_DAYS = 180;

    private final String label;

    CustomerSegment(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @param ordersInWindow   compras en la ventana de {@value #WINDOW_DAYS} días
     * @param daysSinceFirst   días desde la primera compra (todo el historial)
     * @param daysSinceLast    días desde la última compra (todo el historial)
     */
    public static CustomerSegment classify(long ordersInWindow, long daysSinceFirst, long daysSinceLast) {
        if (daysSinceLast > DORMANT_AFTER_DAYS) {
            return DORMANT;
        }
        if (ordersInWindow == 1 && daysSinceFirst <= NEW_MAX_DAYS) {
            return NEW;
        }
        if (ordersInWindow >= FREQUENT_MIN_ORDERS && daysSinceLast <= FREQUENT_MAX_DAYS) {
            return FREQUENT;
        }
        if (ordersInWindow >= AT_RISK_MIN_ORDERS && daysSinceLast > FREQUENT_MAX_DAYS) {
            return AT_RISK;
        }
        return OCCASIONAL;
    }

    /** Sin valor: {@code null} (todos). Código desconocido: 400 {@code INVALID_SEGMENT}. */
    public static CustomerSegment parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("INVALID_SEGMENT",
                    "El segmento debe ser NEW, FREQUENT, AT_RISK, DORMANT u OCCASIONAL.");
        }
    }
}
