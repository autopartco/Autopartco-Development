package com.autopartco.backend.marketplace.checkout;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Estado del pago de un pedido (contrato {@code payments-wompi.md} §3). {@code status} es el estado de Autopartco
 * (PENDING | APPROVED | REJECTED | CANCELLED | EXPIRED); {@code providerStatus}, el crudo de Wompi (solo soporte).
 * {@code checkoutUrl} solo existe mientras el pedido se puede pagar.
 */
public record OrderPaymentView(
        UUID orderId,
        String reference,
        String status,
        String providerStatus,
        String orderStatus,
        long amountInCents,
        String currency,
        String checkoutUrl,
        LocalDateTime expiresAt,
        LocalDateTime paidAt
) {
}
