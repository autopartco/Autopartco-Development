package com.autopartco.backend.storesuite.inventory;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Transferencia entre sucursales. {@code ALL}: todo el stock de la sucursal origen (sin {@code items}).
 * {@code ITEMS}: 1..500 productos sin repetir con su cantidad. {@code idempotencyKey}: una por intento; repetida
 * devuelve la transferencia ya hecha.
 */
public record TransferRequest(
        @NotNull UUID fromBranchId,
        @NotNull UUID toBranchId,
        @NotNull @Pattern(regexp = "ALL|ITEMS", message = "El modo debe ser ALL o ITEMS.") String mode,
        @Size(max = 500) List<@NotNull @Valid Item> items,
        @Size(max = 255) String notes,
        @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:-]+",
                message = "La clave de idempotencia solo admite letras, números y . _ : -") String idempotencyKey
) {
    public record Item(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity
    ) {
    }
}
