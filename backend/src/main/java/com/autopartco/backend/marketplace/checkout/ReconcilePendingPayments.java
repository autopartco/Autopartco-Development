package com.autopartco.backend.marketplace.checkout;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación de pagos Wompi del marketplace (ADR 0004 §2.8): toma los pagos abiertos (también los vencidos que aún no
 * se cerraron, para no cancelar un pago aprobado tarde) y con más de
 * {@value #MIN_AGE_SECONDS} s ({@code FOR UPDATE SKIP LOCKED} en una transacción corta), consulta
 * {@code GET /v1/transactions?reference=} fuera de cualquier transacción y aplica cada resultado final nuevo con
 * {@link WompiEventSource#RECONCILIATION} por el router, que abre su propia transacción. Omite los PENDING y lo ya
 * aplicado para no llenar {@code payment_provider_events} de filas IGNORED.
 */
@Slf4j
@Service
class ReconcilePendingPayments {
    static final long MIN_AGE_SECONDS = 120;

    private final OrderPayments orderPayments;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final PaymentSettings paymentSettings;
    private final TransactionOperations transactions;

    @Autowired
    ReconcilePendingPayments(
            OrderPayments orderPayments,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSettings paymentSettings,
            PlatformTransactionManager transactionManager
    ) {
        this(orderPayments, wompiClient, eventRouter, paymentSettings, new TransactionTemplate(transactionManager));
    }

    ReconcilePendingPayments(
            OrderPayments orderPayments,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSettings paymentSettings,
            TransactionOperations transactions
    ) {
        this.orderPayments = orderPayments;
        this.wompiClient = wompiClient;
        this.eventRouter = eventRouter;
        this.paymentSettings = paymentSettings;
        this.transactions = transactions;
    }

    /** Concilia hasta {@code limit} pagos; devuelve cuántas transacciones aplicó. Se detiene si Wompi no responde. */
    public int reconcile(int limit) {
        List<OrderPayments.ReconciliationCandidate> candidates = transactions.execute(status ->
                orderPayments.lockReconciliationCandidates(
                        MarketplacePaymentLinks.PROVIDER,
                        paymentSettings.wompiEnvironment().name(),
                        MIN_AGE_SECONDS,
                        limit));
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (OrderPayments.ReconciliationCandidate candidate : candidates) {
            List<WompiTransaction> found;
            try {
                found = wompiClient.findTransactionsByReference(candidate.reference());
            } catch (WompiUnavailableException exception) {
                log.warn("Wompi reconciliation stopped: {}", exception.getMessage());
                return applied;
            }
            for (WompiTransaction transaction : found) {
                if (!candidate.reference().equals(transaction.reference()) || transaction.pending()
                        || alreadyApplied(candidate, transaction)) {
                    continue;
                }
                try {
                    eventRouter.apply(transaction, WompiEventSource.RECONCILIATION);
                    applied++;
                } catch (RuntimeException exception) {
                    log.error("Wompi reconciliation failed for reference {} transaction {}",
                            candidate.reference(), transaction.id(), exception);
                }
            }
        }
        return applied;
    }

    private static boolean alreadyApplied(OrderPayments.ReconciliationCandidate candidate, WompiTransaction tx) {
        return tx.id().equals(candidate.providerTransactionId()) && tx.status().equals(candidate.providerStatus());
    }
}
