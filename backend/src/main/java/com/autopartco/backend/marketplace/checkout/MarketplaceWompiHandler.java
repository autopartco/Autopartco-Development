package com.autopartco.backend.marketplace.checkout;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica las transacciones de Wompi de los pedidos del marketplace ({@code APM-…}) dentro de la transacción del
 * router de {@code core.payments}, sin red (ADR 0004 §2.4-§2.7).
 * <ul>
 *   <li>Bloquea el pago y luego el pedido; el vencimiento usa el mismo orden, así que gana uno solo.</li>
 *   <li>Valida entorno, monto en centavos ({@code amount.movePointRight(2).longValueExact()}) y moneda COP.</li>
 *   <li>APPROVED con el pedido aún en espera: pago APPROVED y pedido CONFIRMED. Con el pedido ya vencido o
 *       cancelado: pago APPROVED con {@code requires_manual_refund} y log ERROR; el pedido sigue CANCELLED.</li>
 *   <li>DECLINED/ERROR → REJECTED, VOIDED → CANCELLED; el pedido sigue AWAITING_PAYMENT hasta vencer.</li>
 *   <li>Idempotente por {@code provider_transaction_id}: repetir la misma transacción da IGNORED.</li>
 * </ul>
 * La reserva sigue vigente mientras el pedido está AWAITING_PAYMENT: una aprobación que llega pasado
 * {@code expires_at} pero antes de que corra el vencimiento confirma el pedido, porque el stock aún no se liberó.
 */
@Slf4j
@Component
@AllArgsConstructor
class MarketplaceWompiHandler implements WompiTransactionHandler {
    static final String REFERENCE_PREFIX = StartMarketplaceCheckout.REFERENCE_PREFIX;

    private final OrderPayments orderPayments;
    private final PaymentSettings paymentSettings;

    @Override
    public boolean supports(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX);
    }

    @Override
    public WompiEventOutcome apply(WompiTransaction transaction, WompiEventSource source) {
        Optional<OrderPayment> found =
                orderPayments.lockByReference(MarketplacePaymentLinks.PROVIDER, transaction.reference());
        if (found.isEmpty()) {
            return WompiEventOutcome.UNKNOWN_REFERENCE;
        }
        OrderPayment payment = found.get();
        if (!paymentSettings.wompiEnvironment().name().equals(payment.environment())) {
            log.error("Wompi transaction {} targets order payment {} of environment {}",
                    transaction.id(), payment.reference(), payment.environment());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        long expectedCents;
        try {
            expectedCents = MarketplacePaymentLinks.amountInCents(payment.amount());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Order payment " + payment.paymentId() + " has a non-cent amount",
                    exception);
        }
        if (expectedCents != transaction.amountInCents()
                || !MarketplacePaymentLinks.CURRENCY.equals(transaction.currency())
                || !transaction.currency().equals(payment.currency())) {
            log.error("Wompi transaction {} amount or currency does not match order payment {}",
                    transaction.id(), payment.reference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (transaction.approved()) {
            return approve(payment, transaction, source);
        }
        if (transaction.failed()) {
            return fail(payment, transaction);
        }
        if (!transaction.pending()) {
            log.warn("Unsupported Wompi status {} for order payment {}", transaction.status(), payment.reference());
        }
        return WompiEventOutcome.IGNORED;
    }

    private WompiEventOutcome approve(OrderPayment payment, WompiTransaction transaction, WompiEventSource source) {
        if ("APPROVED".equals(payment.status())) {
            if (transaction.id().equals(payment.providerTransactionId())) {
                return WompiEventOutcome.IGNORED;
            }
            // Segundo cobro aprobado sobre una referencia ya pagada.
            orderPayments.flagManualRefund(payment.paymentId());
            log.error("Second approved Wompi transaction {} for already paid order payment {}; manual refund required",
                    transaction.id(), payment.reference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (linkedElsewhere(payment, transaction)) {
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        boolean stillAwaiting = OrderPayments.AWAITING_PAYMENT.equals(payment.orderStatus())
                && MarketplacePaymentLinks.OPEN_STATUSES.contains(payment.status());
        if (stillAwaiting) {
            orderPayments.markApproved(payment.paymentId(), transaction.id(), transaction.status(), false,
                    "Pago Wompi aprobado");
            if (!orderPayments.confirmAwaitingOrder(payment.orderId())) {
                throw new IllegalStateException("Order " + payment.orderId() + " left AWAITING_PAYMENT while locked");
            }
            log.info("Order {} confirmed by Wompi transaction {} (source={})",
                    payment.orderId(), transaction.id(), source);
            return WompiEventOutcome.APPLIED;
        }
        orderPayments.markApproved(payment.paymentId(), transaction.id(), transaction.status(), true,
                "Pago Wompi aprobado tarde; requiere reembolso manual");
        log.error("Late approved Wompi transaction {} for order {} (order status {}, payment status {}); "
                        + "manual refund required",
                transaction.id(), payment.orderId(), payment.orderStatus(), payment.status());
        return WompiEventOutcome.APPLIED;
    }

    private WompiEventOutcome fail(OrderPayment payment, WompiTransaction transaction) {
        if ("APPROVED".equals(payment.status()) && WompiTransaction.VOIDED.equals(transaction.status())
                && transaction.id().equals(payment.providerTransactionId())) {
            // Wompi anuló el cobro ya aplicado: no se toca el pedido; se marca para revisión manual.
            if (payment.requiresManualRefund()) {
                return WompiEventOutcome.IGNORED;
            }
            orderPayments.flagManualRefund(payment.paymentId());
            log.error("Approved Wompi transaction {} of order {} was VOIDED; manual review required",
                    transaction.id(), payment.orderId());
            return WompiEventOutcome.APPLIED;
        }
        if (!MarketplacePaymentLinks.OPEN_STATUSES.contains(payment.status())) {
            // APPROVED o EXPIRED: un rechazo no los cambia.
            return WompiEventOutcome.IGNORED;
        }
        if (transaction.id().equals(payment.providerTransactionId())
                && transaction.status().equals(payment.providerStatus())) {
            return WompiEventOutcome.IGNORED;
        }
        if (linkedElsewhere(payment, transaction)) {
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        String statusCode = WompiTransaction.VOIDED.equals(transaction.status()) ? "CANCELLED" : "REJECTED";
        orderPayments.markFailed(payment.paymentId(), statusCode, transaction.id(), transaction.status(),
                "Pago Wompi " + transaction.status());
        return WompiEventOutcome.APPLIED;
    }

    private boolean linkedElsewhere(OrderPayment payment, WompiTransaction transaction) {
        if (transaction.id().equals(payment.providerTransactionId())) {
            return false;
        }
        if (orderPayments.transactionLinkedToOtherPayment(
                MarketplacePaymentLinks.PROVIDER, transaction.id(), payment.paymentId())) {
            log.error("Wompi transaction {} is already linked to another order payment", transaction.id());
            return true;
        }
        return false;
    }
}
