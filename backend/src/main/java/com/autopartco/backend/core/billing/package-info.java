/**
 * Planes, suscripción, cobros de suscripción (handler Wompi {@code APC-} del SPI de {@code core.payments}) y
 * derechos de plan.
 */
@ApplicationModule(
        displayName = "Core: Billing",
        allowedDependencies = {"shared", "core.identity", "core.organization", "core.catalog", "core.payments"})
package com.autopartco.backend.core.billing;

import org.springframework.modulith.ApplicationModule;
