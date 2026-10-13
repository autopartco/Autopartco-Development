package com.autopartco.backend.core.billing;

public record CheckoutResponse(
        String reference,
        String checkoutUrl,
        long amountInCents,
        String currency,
        int prepaidMonths
) {
}
