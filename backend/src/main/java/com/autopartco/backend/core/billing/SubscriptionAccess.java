package com.autopartco.backend.core.billing;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vista de la suscripción vigente de un almacén. {@code plan} es el plan efectivo: el pagado si sigue vigente; si
 * no, el de prueba.
 */
public record SubscriptionAccess(
        UUID storeId,
        PlanResponse plan,
        PlanResponse selectedPlan,
        boolean subscriptionExpired,
        LocalDateTime validThrough,
        LocalDateTime trialEndsAt,
        LocalDateTime paidThrough
) {
    public List<String> capabilities() {
        return plan.capabilities() == null ? List.of() : List.copyOf(plan.capabilities());
    }
}
