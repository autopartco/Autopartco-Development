package com.autopartco.backend.marketplace.checkout;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/v1/marketplace/orders} con el proveedor WOMPI (contrato {@code payments-wompi.md}
 * §2.2). La misma forma sirve para el inicio (202) y para la repetición (200).
 *
 * @param status estado del pedido: AWAITING_PAYMENT | CONFIRMED | CANCELLED | …
 */
public record WompiCheckoutResponse(UUID orderId, long orderNumber, String status, Payment payment) {

    /**
     * @param checkoutUrl enlace firmado del Web Checkout; {@code null} si el pedido ya no se puede pagar
     * @param expiresAt   vencimiento de la reserva en hora de Colombia
     */
    public record Payment(
            String reference,
            long amountInCents,
            String currency,
            String checkoutUrl,
            LocalDateTime expiresAt
    ) {
    }
}
