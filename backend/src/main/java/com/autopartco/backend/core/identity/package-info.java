/**
 * Cuentas, personas, sesiones, tokens, perfil y direcciones.
 */
@ApplicationModule(
        displayName = "Core: Identity",
        allowedDependencies = {"shared", "core.audit", "core.notification"})
package com.autopartco.backend.core.identity;

import org.springframework.modulith.ApplicationModule;
