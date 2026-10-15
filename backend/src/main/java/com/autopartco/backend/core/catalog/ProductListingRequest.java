package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Alta de un listado (producto en una sucursal) para un producto que ya existe en el almacén. */
public record ProductListingRequest(
        @NotNull UUID branchId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @PositiveOrZero Integer stock
) {
}
