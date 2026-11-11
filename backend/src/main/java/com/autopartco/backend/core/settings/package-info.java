/**
 * Configuraciones de cuenta ({@code accounts.configuration}) y de almacén ({@code store.configuration}): esquema fijo,
 * valores por defecto, validación y PATCH parcial. Otros módulos leen con {@link SettingsQueries}.
 * La 2FA ({@code security.twoFactor}) solo se cambia con el flujo de código de {@code core.identity}.
 */
@ApplicationModule(
        displayName = "Core: Settings",
        allowedDependencies = {"shared", "core.identity", "core.organization", "core.catalog", "core.audit"})
package com.autopartco.backend.core.settings;

import org.springframework.modulith.ApplicationModule;
