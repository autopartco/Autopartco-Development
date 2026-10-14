package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionEvent;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionEventRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPeriods;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Ciclo de la suscripción de un almacén (columnas de suscripción de {@code seller_applications}).
 */
@Service
@AllArgsConstructor
public class Subscriptions {
    private static final int TRIAL_MONTHS = 1;

    private final StoreSubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final PlanService planService;

    /**
     * Inicia la prueba gratuita. Idempotente: si ya había prueba, no cambia nada y no registra evento.
     * Debe ejecutarse dentro de la transacción que bloquea la solicitud.
     *
     * @return fin de la prueba (nuevo o existente)
     */
    @Transactional
    public LocalDateTime startTrial(UUID storeId, LocalDateTime startsAt) {
        StoreSubscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        if (subscription.getTrialStartsAt() != null) {
            return subscription.getTrialEndsAt();
        }
        Plan trialPlan = planService.trialPlan();
        LocalDateTime trialEndsAt = SubscriptionPeriods.trialEnd(startsAt, TRIAL_MONTHS);
        subscription.setPlan(trialPlan);
        subscription.setTrialStartsAt(startsAt);
        subscription.setTrialEndsAt(trialEndsAt);
        eventRepository.save(SubscriptionEvent.builder()
                .applicationId(subscription.getId())
                .storeId(storeId)
                .eventType("TRIAL_STARTED")
                .planId(trialPlan.getId())
                .details(Map.of())
                .occurredAt(startsAt)
                .build());
        return trialEndsAt;
    }

    /** Vista de la suscripción en {@code now}. El llamador ya comprobó {@code STORE_READ}. */
    @Transactional(readOnly = true)
    public SubscriptionView view(UUID storeId, LocalDateTime now) {
        StoreSubscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        boolean paid = subscription.getPaidThrough() != null && subscription.getPaidThrough().isAfter(now);
        Plan effectivePlan = paid && subscription.getPlan() != null
                ? subscription.getPlan()
                : planService.trialPlan();
        LocalDateTime validThrough = paid ? subscription.getPaidThrough() : subscription.getTrialEndsAt();
        return new SubscriptionView(
                storeId,
                SubscriptionPeriods.statusAt(
                        subscription.getStatus(),
                        subscription.getPaidThrough(),
                        subscription.getTrialEndsAt(),
                        now
                ),
                planService.toResponse(effectivePlan),
                subscription.getSelectedPlan() == null ? null : planService.toResponse(subscription.getSelectedPlan()),
                subscription.getTrialStartsAt(),
                subscription.getTrialEndsAt(),
                subscription.getPaidThrough(),
                validThrough,
                daysRemaining(validThrough, now)
        );
    }

    /** Historial de pagos del almacén, más reciente primero. El llamador ya comprobó {@code STORE_READ}. */
    @Transactional(readOnly = true)
    public List<SubscriptionPaymentView> payments(UUID storeId) {
        StoreSubscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        return paymentRepository.findByStoreApplicationIdOrderByCreatedAtDesc(subscription.getId()).stream()
                .map(payment -> new SubscriptionPaymentView(
                        payment.getId(),
                        payment.getReference(),
                        payment.getProvider(),
                        payment.getPlan().getCode(),
                        payment.getPlan().getName(),
                        payment.getPrepaidMonths(),
                        payment.getAmountInCents() / 100,
                        payment.getCurrency(),
                        payment.getStatus(),
                        payment.getCreatedAt(),
                        payment.getPaidAt(),
                        payment.getPeriodStartsAt(),
                        payment.getPeriodEndsAt()
                ))
                .toList();
    }

    private static Long daysRemaining(LocalDateTime validThrough, LocalDateTime now) {
        if (validThrough == null) {
            return null;
        }
        return Math.max(0, ChronoUnit.DAYS.between(now, validThrough));
    }
}
