package com.autopartco.backend.core.payments;

import java.time.Instant;
import java.util.Objects;

/**
 * Datos de un enlace de Web Checkout. El monto lo calcula siempre el servidor.
 *
 * @param reference     referencia única del pago ({@code APC-…}, {@code APM-…})
 * @param amountInCents monto en centavos enteros (&gt; 0)
 * @param currency      moneda (COP)
 * @param redirectUrl   URL del frontend a la que vuelve el navegador
 * @param expiresAt     vencimiento del enlace ({@code expiration-time}, en UTC) o {@code null} si no vence
 */
public record WompiCheckoutRequest(
        String reference,
        long amountInCents,
        String currency,
        String redirectUrl,
        Instant expiresAt
) {
    public WompiCheckoutRequest {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(redirectUrl, "redirectUrl");
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("amountInCents must be positive");
        }
    }
}
