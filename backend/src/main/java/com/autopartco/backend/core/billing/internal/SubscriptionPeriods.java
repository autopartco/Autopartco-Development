package com.autopartco.backend.core.billing.internal;

import java.time.LocalDateTime;

import com.autopartco.backend.core.billing.SubscriptionStatus;
import com.autopartco.backend.core.organization.StoreApplicationStatus;

/** Reglas de vigencia compartidas por la prueba, la vista y el cobro simulado. Son funciones puras. */
public final class SubscriptionPeriods {

    private SubscriptionPeriods() {
    }

    /**
     * Estado en {@code now}: solicitud distinta de ACTIVE → PENDING_SETUP; pagado vigente → ACTIVE; prueba vigente →
     * TRIALING; si no, EXPIRED.
     */
    public static SubscriptionStatus statusAt(
            StoreApplicationStatus applicationStatus,
            LocalDateTime paidThrough,
            LocalDateTime trialEndsAt,
            LocalDateTime now
    ) {
        if (applicationStatus != StoreApplicationStatus.ACTIVE) {
            return SubscriptionStatus.PENDING_SETUP;
        }
        if (paidThrough != null && paidThrough.isAfter(now)) {
            return SubscriptionStatus.ACTIVE;
        }
        if (trialEndsAt != null && trialEndsAt.isAfter(now)) {
            return SubscriptionStatus.TRIALING;
        }
        return SubscriptionStatus.EXPIRED;
    }

    /** Fin de la prueba: {@code startsAt} más {@code months} meses calendario. */
    public static LocalDateTime trialEnd(LocalDateTime startsAt, int months) {
        return startsAt.plusMonths(months);
    }
}
