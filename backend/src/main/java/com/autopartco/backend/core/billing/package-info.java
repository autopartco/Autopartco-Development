/**
 * Planes, suscripción, cobros de suscripción, adaptador Wompi y derechos de plan.
 */
@ApplicationModule(
        displayName = "Core: Billing",
        allowedDependencies = {"shared", "core.identity", "core.organization", "core.catalog"})
package com.autopartco.backend.core.billing;

import org.springframework.modulith.ApplicationModule;
