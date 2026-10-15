package com.autopartco.backend.storesuite.subscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.billing.PaymentMode;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.SubscriptionStatus;
import com.autopartco.backend.core.billing.SubscriptionView;

/**
 * Suscripción del almacén para el portal (plan §4.3): la vista de {@code core.billing} más el uso frente a los
 * cupos del plan efectivo, sus capacidades y los datos de permiso y cobro.
 */
public record SubscriptionResponse(
        UUID storeId,
        SubscriptionStatus status,
        PlanResponse plan,
        PlanResponse selectedPlan,
        LocalDateTime trialStartsAt,
        LocalDateTime trialEndsAt,
        LocalDateTime paidThrough,
        LocalDateTime validThrough,
        Long daysRemaining,
        Usage usage,
        List<String> capabilities,
        PaymentMode paymentMode,
        boolean canManage,
        boolean planChangeAllowed
) {
    /** Uso y límite de un cupo; {@code limit} nulo significa ilimitado. */
    public record Quota(long used, Integer limit) {
    }

    public record Usage(Quota branches, Quota products, Quota users) {
    }

    static SubscriptionResponse of(
            SubscriptionView view,
            PlanEntitlements.Usage usage,
            PaymentMode paymentMode,
            boolean canManage
    ) {
        PlanResponse plan = view.plan();
        return new SubscriptionResponse(
                view.storeId(),
                view.status(),
                plan,
                view.selectedPlan(),
                view.trialStartsAt(),
                view.trialEndsAt(),
                view.paidThrough(),
                view.validThrough(),
                view.daysRemaining(),
                new Usage(
                        new Quota(usage.branches(), plan.maxBranches()),
                        new Quota(usage.products(), plan.maxProducts()),
                        new Quota(usage.users(), plan.maxUsers())
                ),
                plan.capabilities(),
                paymentMode,
                canManage,
                view.status() != SubscriptionStatus.ACTIVE
        );
    }
}
