package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @Size(max = 200)
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,
        @Size(max = 50) String partNumber,
        @Pattern(regexp = "(?s).*\\S.*", message = "La descripción no puede estar vacía.")
        String description,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @PositiveOrZero Integer quantity,
        UUID manufacturerId,
        UUID categoryId,
        Set<UUID> compatibilityIds,
        @Size(max = 50) Map<String, Object> attributes
) {
    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar.")
    public boolean hasChanges() {
        return name != null || partNumber != null || description != null || price != null
                || quantity != null || manufacturerId != null || categoryId != null
                || compatibilityIds != null || attributes != null;
    }
}