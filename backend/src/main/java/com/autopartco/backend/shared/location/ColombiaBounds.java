package com.autopartco.backend.shared.location;

import java.math.BigDecimal;

/** Rectángulo que contiene a Colombia (incluye San Andrés). Lo usan el mapa de cobertura y la búsqueda por cercanía. */
public final class ColombiaBounds {
    public static final BigDecimal MIN_LAT = new BigDecimal("-4.3");
    public static final BigDecimal MAX_LAT = new BigDecimal("13.5");
    public static final BigDecimal MIN_LNG = new BigDecimal("-82");
    public static final BigDecimal MAX_LNG = new BigDecimal("-66.8");

    private ColombiaBounds() {
    }

    /** {@code true} si el punto está dentro del rectángulo (ambas coordenadas no nulas). */
    public static boolean contains(BigDecimal lat, BigDecimal lng) {
        return lat != null && lng != null
                && lat.compareTo(MIN_LAT) >= 0 && lat.compareTo(MAX_LAT) <= 0
                && lng.compareTo(MIN_LNG) >= 0 && lng.compareTo(MAX_LNG) <= 0;
    }
}
