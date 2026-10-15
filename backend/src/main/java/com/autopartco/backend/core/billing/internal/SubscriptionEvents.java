package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;

/** Escribe el historial de solo inserción de la suscripción. Lo usan el webhook de Wompi y el cobro simulado. */
@Service
@AllArgsConstructor
public class SubscriptionEvents {
    private final SubscriptionEventRepository eventRepository;

    /** Registra {@code PAYMENT_APPROVED} y, si cambió el plan, {@code PLAN_CHANGED}. */
    public void paymentApproved(
            StoreSubscription subscription,
            SubscriptionPayment payment,
            UUID previousPlanId,
            UUID actorAccountId,
            LocalDateTime occurredAt
    ) {
        UUID newPlanId = payment.getPlan().getId();
        eventRepository.save(event(subscription, "PAYMENT_APPROVED", newPlanId, previousPlanId, payment.getId(),
                actorAccountId, occurredAt));
        if (!Objects.equals(previousPlanId, newPlanId)) {
            eventRepository.save(event(subscription, "PLAN_CHANGED", newPlanId, previousPlanId, payment.getId(),
                    actorAccountId, occurredAt));
        }
    }

    private SubscriptionEvent event(
            StoreSubscription subscription,
            String type,
            UUID planId,
            UUID previousPlanId,
            UUID paymentId,
            UUID actorAccountId,
            LocalDateTime occurredAt
    ) {
        return SubscriptionEvent.builder()
                .applicationId(subscription.getId())
                .storeId(subscription.getStoreId())
                .eventType(type)
                .planId(planId)
                .previousPlanId(previousPlanId)
                .paymentId(paymentId)
                .actorAccountId(actorAccountId)
                .details(Map.of())
                .occurredAt(occurredAt)
                .build();
    }
}
