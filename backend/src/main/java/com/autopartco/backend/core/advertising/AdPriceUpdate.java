package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** Tarifa que fija el admin ({@code PUT /admin/ads/prices}). {@code active} nulo conserva el valor actual (o true). */
public record AdPriceUpdate(
        @NotNull AdPlacement placement,
        @NotNull @DecimalMin(value = "1.00") @DecimalMax(value = "10000000.00") @Digits(integer = 12, fraction = 2)
        BigDecimal dailyPrice,
        Boolean active
) {
}
