package com.autopartco.backend.core.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de {@code POST …/subscription/payments/{reference}/sync}: el {@code id} de la redirección de Wompi. */
public record SubscriptionPaymentSyncRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$", message = "El identificador de la transacción no es válido.")
        String transactionId
) {
}
