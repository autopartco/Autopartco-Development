package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiTransactionHandler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica las transacciones de Wompi de las suscripciones ({@code APC-…}) sobre {@code seller_payments}: cuadra monto,
 * moneda y entorno, aplica el pago de forma idempotente y extiende la vigencia pagada. {@code seller_payments.status}
 * guarda el estado crudo de Wompi (el portal lo usa). Corre dentro de la transacción de {@code core.payments}.
 */
@Component
@AllArgsConstructor
@Slf4j
public class SubscriptionWompiHandler implements WompiTransactionHandler {
    public static final String REFERENCE_PREFIX = "APC-";

    private final SubscriptionPaymentRepository paymentRepository;
    private final StoreSubscriptionRepository applicationRepository;
    private final SubscriptionEvents subscriptionEvents;
    private final WompiProperties wompiProperties;

    @Override
    public boolean supports(String reference) {
        return reference != null && reference.startsWith(REFERENCE_PREFIX);
    }

    @Override
    public WompiEventOutcome apply(WompiTransaction transaction, WompiEventSource source) {
        Optional<SubscriptionPayment> found = paymentRepository.lockByReference(transaction.reference());
        if (found.isEmpty()) {
            return WompiEventOutcome.UNKNOWN_REFERENCE;
        }
        SubscriptionPayment payment = found.get();
        if (!"WOMPI".equals(payment.getProvider())
                || (payment.getEnvironment() != null
                        && !payment.getEnvironment().equals(wompiProperties.environment().name()))) {
            log.error("Wompi transaction {} targets subscription payment {} of another provider or environment",
                    transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (transaction.amountInCents() != payment.getAmountInCents()
                || !payment.getCurrency().equals(transaction.currency())) {
            log.error("Wompi transaction {} amount or currency does not match subscription payment {}",
                    transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (transaction.approved()) {
            return approve(payment, transaction);
        }
        if (transaction.failed()) {
            return fail(payment, transaction);
        }
        if (!transaction.pending()) {
            log.warn("Unsupported Wompi status {} for subscription payment {}",
                    transaction.status(), payment.getReference());
        }
        return WompiEventOutcome.IGNORED;
    }

    private WompiEventOutcome approve(SubscriptionPayment payment, WompiTransaction transaction) {
        if ("APPROVED".equals(payment.getStatus())) {
            if (transaction.id().equals(payment.getProviderTransactionId())) {
                return WompiEventOutcome.IGNORED;
            }
            // Segundo cobro aprobado sobre una referencia ya pagada: se marca para reembolso manual.
            payment.setRequiresManualRefund(true);
            log.error("Second approved Wompi transaction {} for already paid subscription payment {}; "
                    + "manual refund required", transaction.id(), payment.getReference());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        if (!transaction.id().equals(payment.getProviderTransactionId())
                && paymentRepository.existsByProviderTransactionId(transaction.id())) {
            log.error("Wompi transaction {} is already linked to another subscription payment", transaction.id());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        StoreSubscription application = applicationRepository.lockById(payment.getStoreApplicationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription row missing for seller payment " + payment.getId()));
        payment.setStatus("APPROVED");
        payment.setProviderTransactionId(transaction.id());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = SubscriptionPeriods.periodStart(
                now, application.getTrialEndsAt(), application.getPaidThrough());
        LocalDateTime periodEnd = periodStart.plusMonths(payment.getPrepaidMonths());
        UUID previousPlanId = application.getPlan() == null ? null : application.getPlan().getId();
        application.setPlan(payment.getPlan());
        application.setPaidThrough(periodEnd);
        payment.setPaidAt(now);
        payment.setPeriodStartsAt(periodStart);
        payment.setPeriodEndsAt(periodEnd);
        subscriptionEvents.paymentApproved(application, payment, previousPlanId, null, now);
        return WompiEventOutcome.APPLIED;
    }

    private WompiEventOutcome fail(SubscriptionPayment payment, WompiTransaction transaction) {
        if ("APPROVED".equals(payment.getStatus())) {
            if (WompiTransaction.VOIDED.equals(transaction.status())
                    && transaction.id().equals(payment.getProviderTransactionId())
                    && !payment.isRequiresManualRefund()) {
                // Wompi anuló el cobro ya aplicado: la vigencia no cambia; se marca para revisión manual.
                payment.setRequiresManualRefund(true);
                log.error("Approved Wompi transaction {} of subscription payment {} was VOIDED; manual review "
                        + "required", transaction.id(), payment.getReference());
                return WompiEventOutcome.APPLIED;
            }
            return WompiEventOutcome.IGNORED;
        }
        if (transaction.status().equals(payment.getStatus())
                && transaction.id().equals(payment.getProviderTransactionId())) {
            return WompiEventOutcome.IGNORED;
        }
        if (!transaction.id().equals(payment.getProviderTransactionId())
                && paymentRepository.existsByProviderTransactionId(transaction.id())) {
            log.error("Wompi transaction {} is already linked to another subscription payment", transaction.id());
            return WompiEventOutcome.REJECTED_MISMATCH;
        }
        payment.setStatus(transaction.status());
        payment.setProviderTransactionId(transaction.id());
        return WompiEventOutcome.APPLIED;
    }
}
