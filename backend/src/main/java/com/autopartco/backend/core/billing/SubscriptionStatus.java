package com.autopartco.backend.core.billing;

/** Estado de la suscripción de un almacén, derivado en lectura (SA-B2, plan §4.3). */
public enum SubscriptionStatus {
    PENDING_SETUP,
    TRIALING,
    ACTIVE,
    EXPIRED
}
