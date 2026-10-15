package com.autopartco.backend.marketplace.checkout;

import java.math.BigDecimal;

/**
 * Intento de cobro que el caso de uso entrega al {@link CheckoutPaymentGateway}. {@code reference} es el
 * {@code clientRequestId}; {@code simulatedOutcome} solo lo interpreta el simulador.
 */
public record PaymentAttempt(
        String methodCode,
        BigDecimal amount,
        String currency,
        String reference,
        String simulatedOutcome
) {
}
