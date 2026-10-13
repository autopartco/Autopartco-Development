package com.autopartco.backend.shared.persistence;

/**
 * Códigos de la tabla {@code states} referenciados desde el código ({@link State}).
 * No cubre estados de dominio con enum propio ni estados de pago o de venta.
 */
public final class StateCodes {
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String PENDING = "PENDING";
    public static final String LOCKED = "LOCKED";

    private StateCodes() {
    }
}
