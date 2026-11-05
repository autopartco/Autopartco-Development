package com.autopartco.backend.marketplace.checkout;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.inventory.StockReturn;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * Vence los pagos Wompi abiertos cuyo {@code expires_at} ya pasó (ADR 0004 §2.4): pago EXPIRED, pedido CANCELLED con
 * motivo {@code PAYMENT_TIMEOUT} y {@code cancelled_at}, ventas CANCELLED y stock devuelto (sin
 * {@code inventory_movements}, N14).
 * <ol>
 *   <li>Antes de vencer cada pago consulta {@code GET /v1/transactions?reference=} fuera de cualquier transacción y,
 *       si hay un APPROVED, lo aplica por el router ({@link WompiEventSource#RECONCILIATION}): el pedido se confirma
 *       en vez de cancelarse. Si Wompi no responde, ese pago no vence en esta ronda.</li>
 *   <li>Cada vencimiento corre en su propia transacción y vuelve a tomar la fila con {@code FOR UPDATE SKIP LOCKED}
 *       exigiendo que siga abierta y vencida: si un evento de Wompi la está aplicando o ya la aprobó, no vence.</li>
 * </ol>
 * Los pagos de otro entorno no se pueden consultar con estas llaves y vencen sin consulta.
 */
@Slf4j
@Service
class ExpireAwaitingPayments {
    static final String CANCELLATION_REASON = "PAYMENT_TIMEOUT";

    private final OrderPayments orderPayments;
    private final StockService stockService;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final PaymentSettings paymentSettings;
    private final TransactionOperations transactions;

    @Autowired
    ExpireAwaitingPayments(
            OrderPayments orderPayments,
            StockService stockService,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSettings paymentSettings,
            PlatformTransactionManager transactionManager
    ) {
        this(orderPayments, stockService, wompiClient, eventRouter, paymentSettings,
                new TransactionTemplate(transactionManager));
    }

    ExpireAwaitingPayments(
            OrderPayments orderPayments,
            StockService stockService,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSettings paymentSettings,
            TransactionOperations transactions
    ) {
        this.orderPayments = orderPayments;
        this.stockService = stockService;
        this.wompiClient = wompiClient;
        this.eventRouter = eventRouter;
        this.paymentSettings = paymentSettings;
        this.transactions = transactions;
    }

    /**
     * Resultado de una tanda: candidatos leídos, vencidos, si Wompi dejó de responder y el último candidato (cursor
     * de la tanda siguiente; {@code null} si no hubo).
     */
    record Batch(int candidates, int expired, boolean wompiUnavailable, OrderPayments.ExpiredCandidate last) {
    }

    /** Primera tanda de la ronda. */
    Batch expireBatch(int limit) {
        return expireBatch(limit, null, false);
    }

    /**
     * Procesa hasta {@code limit} pagos vencidos posteriores a {@code after} (paginación por
     * {@code (expires_at, id)}). Con {@code wompiKnownDown} no vuelve a llamar a Wompi en esta ronda y omite los
     * pagos que habría que consultar.
     */
    Batch expireBatch(int limit, OrderPayments.ExpiredCandidate after, boolean wompiKnownDown) {
        List<OrderPayments.ExpiredCandidate> candidates =
                orderPayments.findExpired(MarketplacePaymentLinks.PROVIDER, after, limit);
        int expired = 0;
        boolean wompiUnavailable = wompiKnownDown;
        String environment = paymentSettings.wompiEnvironment().name();
        for (OrderPayments.ExpiredCandidate candidate : candidates) {
            if (environment.equals(candidate.environment())) {
                if (wompiUnavailable) {
                    continue;
                }
                try {
                    if (!applyLateApproval(candidate)) {
                        continue;
                    }
                } catch (WompiUnavailableException exception) {
                    log.warn("Wompi lookup failed before expiring payments; retrying next round: {}",
                            exception.getMessage());
                    wompiUnavailable = true;
                    continue;
                }
            }
            if (Boolean.TRUE.equals(transactions.execute(status -> expire(candidate)))) {
                expired++;
            }
        }
        return new Batch(candidates.size(), expired, wompiUnavailable,
                candidates.isEmpty() ? null : candidates.getLast());
    }

    /**
     * Aplica un APPROVED de Wompi para esa referencia, si existe. {@code false} si no se debe vencer el pago en esta
     * ronda (falló la aplicación).
     */
    private boolean applyLateApproval(OrderPayments.ExpiredCandidate candidate) {
        for (WompiTransaction transaction : wompiClient.findTransactionsByReference(candidate.reference())) {
            if (!candidate.reference().equals(transaction.reference()) || !transaction.approved()) {
                continue;
            }
            try {
                eventRouter.apply(transaction, WompiEventSource.RECONCILIATION);
            } catch (RuntimeException exception) {
                log.error("Could not apply approved Wompi transaction {} before expiring payment {}",
                        transaction.id(), candidate.paymentId(), exception);
                return false;
            }
        }
        return true;
    }

    /** En una transacción: vuelve a bloquear y, si sigue abierto y vencido, cancela, devuelve stock y vence. */
    private boolean expire(OrderPayments.ExpiredCandidate candidate) {
        Optional<OrderPayments.ExpiredPayment> locked = orderPayments.lockExpired(candidate.paymentId());
        if (locked.isEmpty()) {
            return false;
        }
        OrderPayments.ExpiredPayment payment = locked.get();
        if (orderPayments.cancelAwaitingOrder(payment.orderId(), CANCELLATION_REASON)) {
            stockService.returnStock(orderPayments.reservedLines(payment.orderId()).stream()
                    .map(line -> new StockReturn(line.inventoryId(), line.units()))
                    .toList());
            log.info("Order {} cancelled: Wompi payment {} expired; stock released",
                    payment.orderId(), payment.paymentId());
        } else {
            log.warn("Expired Wompi payment {} belongs to order {} that is no longer awaiting payment",
                    payment.paymentId(), payment.orderId());
        }
        orderPayments.markExpired(payment.paymentId(), "Pago vencido sin aprobación");
        return true;
    }
}
