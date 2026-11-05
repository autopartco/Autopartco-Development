package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pago más reciente de un pedido junto con el estado del pedido. Lo leen el handler de Wompi (con bloqueo), la vista
 * del comprador y el inicio del checkout.
 *
 * @param status               código de {@code payment_statuses} (PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED…)
 * @param providerStatus       estado crudo del proveedor o {@code null}
 * @param amount               monto en pesos ({@code DECIMAL(12,2)})
 * @param environment          SANDBOX | PRODUCTION
 * @param expiresAt            vencimiento de la reserva ({@code null} en pagos simulados)
 * @param paidAt               hora local de aprobación o {@code null}
 */
public record OrderPayment(
        UUID paymentId,
        UUID orderId,
        UUID personId,
        long orderNumber,
        String orderStatus,
        String provider,
        String reference,
        String status,
        String providerStatus,
        String providerTransactionId,
        BigDecimal amount,
        String currency,
        String environment,
        Instant expiresAt,
        LocalDateTime paidAt,
        boolean requiresManualRefund
) {
}
