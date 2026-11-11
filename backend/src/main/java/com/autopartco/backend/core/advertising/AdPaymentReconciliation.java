package com.autopartco.backend.core.advertising;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.advertising.internal.AdPayment;
import com.autopartco.backend.core.advertising.internal.AdPaymentRepository;
import com.autopartco.backend.core.advertising.internal.AdWompiHandler;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación de pagos de campañas ({@code ADS-…}) en {@code PENDING}, como {@code SubscriptionPaymentReconciler}:
 * cada 10 min toma los pagos Wompi del entorno actual con más de {@link #MIN_AGE} (índice {@code ix_adp_pending}),
 * consulta {@code GET /v1/transactions?reference=} fuera de cualquier transacción y aplica cada resultado final con
 * {@link WompiEventSource#RECONCILIATION} por el router (queda en {@code payment_provider_events} y lo aplica
 * {@code AdWompiHandler}, idempotente igual que el webhook).
 * <p>
 * Si el pago sigue {@code PENDING} y tiene más de {@link #MAX_AGE} (Wompi no tiene transacción final o no respondió),
 * vence: {@code FAILED} y la campaña {@code PENDING_PAYMENT} vuelve a {@code DRAFT} salvo que tenga otro pago
 * pendiente más reciente. Una aprobación tardía se sigue aplicando. Si Wompi no responde, deja de consultar en esa
 * ronda. Se apaga con {@code app.ads.payment-reconciliation-enabled=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ads", name = "payment-reconciliation-enabled", havingValue = "true",
        matchIfMissing = true)
class AdPaymentReconciliation {
    static final Duration MIN_AGE = Duration.ofMinutes(5);
    static final Duration MAX_AGE = Duration.ofHours(24);
    static final int BATCH_SIZE = 50;

    private final AdPaymentRepository paymentRepository;
    private final AdWompiHandler handler;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final WompiProperties wompiProperties;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    AdPaymentReconciliation(AdPaymentRepository paymentRepository, AdWompiHandler handler, WompiClient wompiClient,
            WompiEventRouter eventRouter, WompiProperties wompiProperties,
            PlatformTransactionManager transactionManager) {
        this(paymentRepository, handler, wompiClient, eventRouter, wompiProperties,
                new TransactionTemplate(transactionManager), Clock.systemDefaultZone());
    }

    AdPaymentReconciliation(AdPaymentRepository paymentRepository, AdWompiHandler handler, WompiClient wompiClient,
            WompiEventRouter eventRouter, WompiProperties wompiProperties, TransactionOperations transactions,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.handler = handler;
        this.wompiClient = wompiClient;
        this.eventRouter = eventRouter;
        this.wompiProperties = wompiProperties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.ads.payment-reconciliation-initial-delay-ms:120000}",
            fixedDelayString = "${app.ads.payment-reconciliation-delay-ms:600000}")
    void run() {
        try {
            Result result = reconcile();
            if (result.applied() + result.expired() > 0) {
                log.info("Ad payment reconciliation: {} Wompi transactions applied, {} payments expired",
                        result.applied(), result.expired());
            }
        } catch (RuntimeException exception) {
            log.error("Ad payment reconciliation failed", exception);
        }
    }

    record Result(int applied, int expired) {
    }

    /** Concilia hasta {@value #BATCH_SIZE} pagos pendientes, los más viejos primero. */
    Result reconcile() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(MAX_AGE);
        List<AdPayment> pending = paymentRepository.findPendingCreatedBefore(AdPayment.WOMPI,
                wompiProperties.environment().name(), now.minus(MIN_AGE), PageRequest.of(0, BATCH_SIZE));
        int applied = 0;
        int expired = 0;
        boolean wompiDown = false;
        for (AdPayment payment : pending) {
            String reference = payment.getReference();
            if (!wompiDown) {
                try {
                    applied += applyFinal(payment, wompiClient.findTransactionsByReference(reference));
                } catch (WompiUnavailableException exception) {
                    log.warn("Ad payment reconciliation stopped querying Wompi: {}", exception.getMessage());
                    wompiDown = true;
                }
            }
            if (payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(staleBefore)) {
                try {
                    if (Boolean.TRUE.equals(transactions.execute(status ->
                            handler.expireStale(reference, staleBefore)))) {
                        expired++;
                    }
                } catch (RuntimeException exception) {
                    log.error("Ad payment expiration failed for reference {}", reference, exception);
                }
            }
        }
        return new Result(applied, expired);
    }

    private int applyFinal(AdPayment payment, List<WompiTransaction> found) {
        int applied = 0;
        for (WompiTransaction transaction : found) {
            if (!payment.getReference().equals(transaction.reference()) || transaction.pending()
                    || alreadyApplied(payment, transaction)) {
                continue;
            }
            try {
                eventRouter.apply(transaction, WompiEventSource.RECONCILIATION);
                applied++;
            } catch (RuntimeException exception) {
                log.error("Wompi reconciliation failed for reference {} transaction {}",
                        payment.getReference(), transaction.id(), exception);
            }
        }
        return applied;
    }

    private static boolean alreadyApplied(AdPayment payment, WompiTransaction transaction) {
        return transaction.id().equals(payment.getProviderTransactionId())
                && transaction.status().equals(payment.getStatus());
    }
}
