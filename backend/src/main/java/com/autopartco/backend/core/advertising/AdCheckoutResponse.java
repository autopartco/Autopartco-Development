package com.autopartco.backend.core.advertising;

import java.util.UUID;

/**
 * Enlace firmado al Web Checkout de Wompi para pagar una campaña (misma forma que el {@code CheckoutResponse} de la
 * suscripción, sin meses prepagados y con la campaña).
 */
public record AdCheckoutResponse(
        String reference,
        String checkoutUrl,
        long amountInCents,
        String currency,
        UUID campaignId
) {
}
