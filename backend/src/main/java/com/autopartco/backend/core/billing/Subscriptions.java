package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
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
    private final PlanService planService;

    /**
     * Inicia la prueba gratuita al completar la configuración del almacén: fija el plan de prueba y la ventana
     * {@code [startsAt, startsAt + 1 mes)}. Debe ejecutarse dentro de la transacción que bloquea la solicitud.
     *
     * @return fin de la prueba
     */
    @Transactional
    public LocalDateTime startTrial(UUID storeId, LocalDateTime startsAt) {
        Plan trialPlan = planService.trialPlan();
        StoreSubscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        subscription.setPlan(trialPlan);
        subscription.setTrialStartsAt(startsAt);
        subscription.setTrialEndsAt(subscription.getTrialStartsAt().plusMonths(TRIAL_MONTHS));
        return subscription.getTrialEndsAt();
    }
}
