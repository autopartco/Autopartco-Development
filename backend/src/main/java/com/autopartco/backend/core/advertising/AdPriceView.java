package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;

/** Tarifa diaria (COP, IVA incluido) de una ubicación. El portal solo recibe las activas. */
public record AdPriceView(AdPlacement placement, BigDecimal dailyPrice, boolean active) {
}
