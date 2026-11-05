package com.autopartco.backend.core.billing.internal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación de pagos de suscripción Wompi PENDING (ADR 0004 §2.8): cada 5 min consulta
 * {@code GET /v1/transactions?reference=} para los pagos con más de 2 min y menos de 24 h, fuera de cualquier
 * transacción, y aplica los resultados finales con {@link WompiEventSource#RECONCILIATION} por el router (que bloquea
 * la fila en {@link SubscriptionWompiHandler}). No hay vencimiento: el pago de suscripción no reserva nada. Se apaga
 * con {@code app.billing.payment-reconciliation-enabled=false}.
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "app.billing", name = "payment-reconciliation-enabled", havingValue = "true",
        matchIfMissing = true)
class SubscriptionPaymentReconciler {
    static final Duration MIN_AGE = Duration.ofMinutes(2);
    static final Duration MAX_AGE = Duration.ofHours(24);
    static final int BATCH_SIZE = 50;

    private final SubscriptionPaymentRepository paymentRepository;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final WompiProperties wompiProperties;

    @Scheduled(
            initialDelayString = "${app.billing.payment-reconciliation-initial-delay-ms:90000}",
            fixedDelayString = "${app.billing.payment-reconciliation-delay-ms:300000}")
    void run() {
        try {
            int applied = reconcile(LocalDateTime.now());
            if (applied > 0) {
                log.info("Wompi reconciliation applied {} subscription transactions", applied);
            }
        } catch (RuntimeException exception) {
            log.error("Subscription payment reconciliation failed", exception);
        }
    }

    /** Devuelve cuántas transacciones aplicó; se detiene si Wompi no responde. */
    int reconcile(LocalDateTime now) {
        List<String> references = paymentRepository.findPendingWompiReferences(
                wompiProperties.environment().name(),
                now.minus(MAX_AGE),
                now.minus(MIN_AGE),
                PageRequest.of(0, BATCH_SIZE));
        int applied = 0;
        for (String reference : references) {
            List<WompiTransaction> found;
            try {
                found = wompiClient.findTransactionsByReference(reference);
            } catch (WompiUnavailableException exception) {
                log.warn("Wompi subscription reconciliation stopped: {}", exception.getMessage());
                return applied;
            }
            for (WompiTransaction transaction : found) {
                if (!reference.equals(transaction.reference()) || transaction.pending()) {
                    continue;
                }
                try {
                    eventRouter.apply(transaction, WompiEventSource.RECONCILIATION);
                    applied++;
                } catch (RuntimeException exception) {
                    log.error("Wompi reconciliation failed for reference {} transaction {}",
                            reference, transaction.id(), exception);
                }
            }
        }
        return applied;
    }
}
