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

    /**
     * Inicio de un periodo pagado nuevo: no antes de {@code now}, ni antes del fin de la prueba ni del periodo pagado
     * vigente. Así un pago anticipado se suma al periodo que ya tiene el almacén.
     */
    public static LocalDateTime periodStart(LocalDateTime now, LocalDateTime trialEndsAt, LocalDateTime paidThrough) {
        LocalDateTime start = now;
        if (trialEndsAt != null && trialEndsAt.isAfter(start)) {
            start = trialEndsAt;
        }
        if (paidThrough != null && paidThrough.isAfter(start)) {
            start = paidThrough;
        }
        return start;
    }

    /** Fin de la prueba: {@code startsAt} más {@code months} meses calendario. */
    public static LocalDateTime trialEnd(LocalDateTime startsAt, int months) {
        return startsAt.plusMonths(months);
    }
}
