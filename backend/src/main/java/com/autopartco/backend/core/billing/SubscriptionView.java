package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Suscripción de un almacén sin datos de permiso. {@code plan} es el efectivo: el pagado vigente o, si no hay,
 * el de prueba. {@code daysRemaining} es nulo si no hay vigencia.
 */
public record SubscriptionView(
        UUID storeId,
        SubscriptionStatus status,
        PlanResponse plan,
        PlanResponse selectedPlan,
        LocalDateTime trialStartsAt,
        LocalDateTime trialEndsAt,
        LocalDateTime paidThrough,
        LocalDateTime validThrough,
        Long daysRemaining
) {
}
