package com.autopartco.backend.marketplace.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de {@code POST /api/v1/me/orders/{orderId}/payment/sync}: el {@code id} de la redirección de Wompi. */
public record OrderPaymentSyncRequest(
        @NotBlank(message = "El identificador de la transacción es obligatorio.")
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$", message = "El identificador de la transacción no es válido.")
        String transactionId
) {
}
