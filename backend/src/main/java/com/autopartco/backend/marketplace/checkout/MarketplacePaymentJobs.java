package com.autopartco.backend.marketplace.checkout;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.order.OrderPayments;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trabajo periódico de pagos del marketplace (cada 5 min por defecto): primero concilia con Wompi (también los pagos
 * vencidos que siguen abiertos) y después vence las reservas, consultando antes cada pago. Seguro con varias
 * instancias gracias a {@code SKIP LOCKED}; usa el {@code TaskScheduler} de {@code core.payments}. Se apaga con
 * {@code app.checkout.payment-jobs-enabled=false}.
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "app.checkout", name = "payment-jobs-enabled", havingValue = "true",
        matchIfMissing = true)
class MarketplacePaymentJobs {
    static final int BATCH_SIZE = 50;
    static final int MAX_EXPIRE_BATCHES = 20;

    private final ReconcilePendingPayments reconcilePendingPayments;
    private final ExpireAwaitingPayments expireAwaitingPayments;

    @Scheduled(
            initialDelayString = "${app.checkout.payment-jobs-initial-delay-ms:60000}",
            fixedDelayString = "${app.checkout.payment-jobs-delay-ms:300000}")
    void run() {
        try {
            int applied = reconcilePendingPayments.reconcile(BATCH_SIZE);
            if (applied > 0) {
                log.info("Wompi reconciliation applied {} marketplace transactions", applied);
            }
        } catch (RuntimeException exception) {
            log.error("Marketplace payment reconciliation failed", exception);
        }
        try {
            expireAll();
        } catch (RuntimeException exception) {
            log.error("Marketplace payment expiration failed", exception);
        }
    }

    /**
     * Recorre los pagos vencidos por tandas paginadas tras el último candidato, aunque una tanda no venza ninguno
     * (Wompi caído o aprobación sin aplicar): así esos pagos no tapan a los posteriores. Lo que no venció se reintenta
     * en la próxima ronda. Devuelve cuántos venció.
     */
    int expireAll() {
        int expired = 0;
        OrderPayments.ExpiredCandidate cursor = null;
        boolean wompiDown = false;
        for (int round = 0; round < MAX_EXPIRE_BATCHES; round++) {
            ExpireAwaitingPayments.Batch batch = expireAwaitingPayments.expireBatch(BATCH_SIZE, cursor, wompiDown);
            expired += batch.expired();
            wompiDown = batch.wompiUnavailable();
            if (batch.candidates() < BATCH_SIZE || batch.last() == null) {
                break;
            }
            cursor = batch.last();
        }
        return expired;
    }
}
