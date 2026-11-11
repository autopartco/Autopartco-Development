package com.autopartco.backend.marketplace.home;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Área del inicio: ubicación redondeada a 0,1° (unos 11 km) y ciudad DIVIPOLA; es la clave de la caché. Sin ubicación,
 * {@code lat} y {@code lng} son nulos.
 */
public record HomeArea(BigDecimal lat, BigDecimal lng, String cityCode) {
    static final int SCALE = 1;

    static HomeArea of(BigDecimal lat, BigDecimal lng, String cityCode) {
        boolean located = lat != null && lng != null;
        return new HomeArea(located ? lat.setScale(SCALE, RoundingMode.HALF_UP) : null,
                located ? lng.setScale(SCALE, RoundingMode.HALF_UP) : null,
                cityCode == null || cityCode.isBlank() ? null : cityCode.strip());
    }

    boolean located() {
        return lat != null && lng != null;
    }
}
