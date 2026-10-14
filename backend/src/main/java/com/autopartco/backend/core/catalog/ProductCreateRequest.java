package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotNull UUID branchId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 50) String partNumber,
        @NotBlank String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @PositiveOrZero Integer quantity,
        @NotNull UUID manufacturerId,
        @NotNull UUID categoryId,
        @NotEmpty Set<UUID> compatibilityIds,
        @Size(max = 50) Map<String, Object> attributes
) {
}