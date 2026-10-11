package com.autopartco.backend.seller.dto;

public record SellerCheckoutResponse(
        String reference,
        String checkoutUrl,
        long amountInCents,
        String currency,
        int prepaidMonths
) {
}
