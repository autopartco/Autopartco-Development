package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.UUID;

/** Pago de suscripción visible para el portal. {@code amountCop} está en pesos enteros. */
public record SubscriptionPaymentView(
        UUID id,
        String reference,
        String provider,
        String planCode,
        String planName,
        int months,
        long amountCop,
        String currency,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime periodStartsAt,
        LocalDateTime periodEndsAt
) {
}
