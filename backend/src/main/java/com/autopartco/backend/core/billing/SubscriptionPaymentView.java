package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.UUID;

import com.autopartco.backend.core.billing.internal.SubscriptionPayment;

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
    static SubscriptionPaymentView from(SubscriptionPayment payment) {
        return new SubscriptionPaymentView(
                payment.getId(),
                payment.getReference(),
                payment.getProvider(),
                payment.getPlan().getCode(),
                payment.getPlan().getName(),
                payment.getPrepaidMonths(),
                payment.getAmountInCents() / 100,
                payment.getCurrency(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt(),
                payment.getPeriodStartsAt(),
                payment.getPeriodEndsAt()
        );
    }
}
