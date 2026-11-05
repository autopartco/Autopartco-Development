package com.autopartco.backend.storesuite.inventory;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ajuste de existencias de un listado. {@code SET}: {@code quantity} es el nuevo stock (≥ 0). {@code DELTA}:
 * {@code quantity} se suma (positivo) o resta (negativo) y no puede ser 0.
 */
public record StockAdjustmentRequest(
        @NotNull UUID branchId,
        @NotNull UUID productId,
        @NotNull @Pattern(regexp = "SET|DELTA", message = "El modo debe ser SET o DELTA.") String mode,
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantity,
        @NotBlank @Size(max = 40) String reasonCode,
        @Size(max = 255) String notes
) {
    public boolean isSet() {
        return "SET".equals(mode);
    }
}
