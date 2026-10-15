package com.autopartco.backend.marketplace.checkout;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/marketplace/orders}. No incluye precios: el servidor los calcula y Jackson rechaza
 * campos desconocidos.
 */
public record PlaceOrderRequest(
        @NotNull(message = "El identificador de la solicitud es obligatorio.")
        UUID clientRequestId,

        @NotNull(message = "La dirección es obligatoria.")
        UUID addressId,

        @NotBlank(message = "El método de pago es obligatorio.")
        String paymentMethod,

        @Pattern(regexp = "APPROVED|REJECTED", message = "El resultado simulado debe ser APPROVED o REJECTED.")
        String simulatedOutcome,

        @NotNull(message = "El pedido debe tener entre 1 y 50 líneas.")
        @Size(min = 1, max = 50, message = "El pedido debe tener entre 1 y 50 líneas.")
        List<@NotNull(message = "La línea del pedido es obligatoria.") @Valid Item> items
) {
    /** Una línea del carrito: un producto en una sucursal y sus unidades. */
    public record Item(
            @NotNull(message = "El producto es obligatorio.")
            UUID productId,

            @NotNull(message = "La sucursal es obligatoria.")
            UUID branchId,

            @NotNull(message = "La cantidad debe estar entre 1 y 99.")
            @Min(value = 1, message = "La cantidad debe estar entre 1 y 99.")
            @Max(value = 99, message = "La cantidad debe estar entre 1 y 99.")
            Integer quantity
    ) {
    }
}
