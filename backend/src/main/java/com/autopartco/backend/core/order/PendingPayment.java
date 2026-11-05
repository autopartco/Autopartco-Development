package com.autopartco.backend.core.order;

import java.time.Instant;
import java.util.Objects;

/**
 * Pago pendiente de un proveedor asíncrono (Wompi) que se crea junto con el pedido en espera de pago.
 *
 * @param provider       proveedor ({@code WOMPI})
 * @param reference      referencia única del pago ({@code APM-…}); va en {@code payments.provider_reference}
 * @param idempotencyKey {@code clientRequestId} del checkout; va en {@code payments.idempotency_key}
 * @param environment    entorno del proveedor ({@code SANDBOX} | {@code PRODUCTION})
 * @param expiresAt      vencimiento de la reserva de stock y del enlace de pago
 */
public record PendingPayment(
        String provider,
        String reference,
        String idempotencyKey,
        String environment,
        Instant expiresAt
) {
    public PendingPayment {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
