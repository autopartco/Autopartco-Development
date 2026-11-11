package com.autopartco.backend.core.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreConfigurations;
import com.autopartco.backend.core.settings.internal.SettingsSchemas;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.events.StoreProfileChanged;

import lombok.AllArgsConstructor;

/**
 * Lectura y PATCH parcial de configuraciones. Las lecturas devuelven el objeto completo con valores por defecto.
 * La autorización la hace el llamador (sesión para la cuenta, {@code STORE_UPDATE} para el almacén).
 *
 * <p>{@code notifications.marketing}: no hay todavía un servicio Java del registro de consentimientos
 * ({@code person_consents}); por ahora solo se guarda la preferencia.</p>
 */
@Service
@AllArgsConstructor
public class SettingsService {
    private final AccountConfigurations accountConfigurations;
    private final StoreConfigurations storeConfigurations;
    private final ListingQueries listingQueries;
    private final SettingsQueries settingsQueries;
    private final AuditService auditService;
    /** {@link StoreProfileChanged} al guardar: vacía las cachés públicas del almacén tras el commit. */
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public AccountSettings accountSettings(UUID accountId) {
        return settingsQueries.accountSettings(accountId);
    }

    @Transactional
    public AccountSettings patchAccount(UUID accountId, Map<String, Object> patch) {
        Map<String, Object> stored = accountConfigurations.lockForUpdate(accountId);
        Map<String, Object> before = SettingsSchemas.ACCOUNT.effective(stored);
        Map<String, Object> after = SettingsSchemas.ACCOUNT.apply(before, patch);

        Object twoFactorBefore = SettingsSchemas.section(before, "security").get("twoFactor");
        Object twoFactorAfter = SettingsSchemas.section(after, "security").get("twoFactor");
        if (!Objects.equals(twoFactorBefore, twoFactorAfter)) {
            throw new BadRequestException("TWO_FACTOR_REQUIRES_CONFIRMATION",
                    "La verificación en dos pasos se activa o desactiva confirmando un código enviado a tu correo.",
                    Map.of("key", "security.twoFactor"));
        }

        Map<String, Object> merged = new HashMap<>(stored);
        merged.putAll(after);
        accountConfigurations.replace(accountId, merged);
        List<String> changed = changedKeys(before, after, "");
        if (!changed.isEmpty()) {
            auditService.record(accountId, "ACCOUNT_SETTINGS_UPDATED", "ACCOUNT", accountId, Map.of("keys", changed));
        }
        return SettingsSchemas.toAccount(after);
    }

    @Transactional(readOnly = true)
    public StoreSettings storeSettings(UUID storeId) {
        return SettingsSchemas.toStore(SettingsSchemas.STORE.effective(storeConfigurations.read(storeId)));
    }

    /**
     * PATCH de la configuración del almacén. Solo escribe las secciones del esquema ({@code description} y
     * {@code publicProfile} quedan intactas). Activar {@code security.requireStaffTwoFactor} exige que quien lo
     * activa tenga 2FA ({@code TWO_FACTOR_REQUIRED_FIRST}). Si el almacén ya lo exige, quien no tiene 2FA no puede
     * cambiar nada, ni siquiera desactivarlo (403 {@code STAFF_TWO_FACTOR_REQUIRED}): la ruta está exenta del
     * {@code StaffTwoFactorInterceptor} solo para poder leerla; la 2FA propia se activa en {@code /account/settings}.
     */
    @Transactional
    public StoreSettings patchStore(UUID storeId, UUID actorAccountId, boolean actorHasTwoFactor, Map<String, Object> patch) {
        Map<String, Object> before = SettingsSchemas.STORE.effective(storeConfigurations.lockForUpdate(storeId));
        boolean requiredBefore = Boolean.TRUE.equals(
                SettingsSchemas.section(before, "security").get("requireStaffTwoFactor"));
        if (requiredBefore && !actorHasTwoFactor) {
            throw new ForbiddenException("STAFF_TWO_FACTOR_REQUIRED",
                    "Este almacén exige verificación en dos pasos. Actívala en la configuración de tu cuenta.",
                    Map.of("storeId", storeId.toString()));
        }
        Map<String, Object> after = SettingsSchemas.STORE.apply(before, patch);

        if (SettingsSchemas.section(patch, "profile").containsKey("featuredProductIds")) {
            requirePublished(storeId, after);
        }
        boolean requiredAfter = Boolean.TRUE.equals(
                SettingsSchemas.section(after, "security").get("requireStaffTwoFactor"));
        if (!requiredBefore && requiredAfter && !actorHasTwoFactor) {
            throw new BadRequestException("TWO_FACTOR_REQUIRED_FIRST",
                    "Activa primero la verificación en dos pasos en tu cuenta para exigirla al equipo.",
                    Map.of("key", "security.requireStaffTwoFactor"));
        }

        storeConfigurations.putSections(storeId, after);
        settingsQueries.invalidateStore(storeId);
        events.publishEvent(new StoreProfileChanged(storeId));
        List<String> changed = changedKeys(before, after, "");
        if (!changed.isEmpty()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("storeId", storeId.toString());
            metadata.put("keys", changed);
            auditService.record(actorAccountId, "STORE_SETTINGS_UPDATED", "STORE", storeId, metadata);
        }
        return SettingsSchemas.toStore(after);
    }

    private void requirePublished(UUID storeId, Map<String, Object> after) {
        List<UUID> featured = SettingsSchemas.toStore(after).profile().featuredProductIds();
        if (featured.isEmpty()) {
            return;
        }
        Set<UUID> published = listingQueries.publishedProductIdsAmong(storeId, featured);
        if (!published.containsAll(featured)) {
            throw new BadRequestException("INVALID_SETTING",
                    "Los productos destacados deben estar publicados y pertenecer al almacén.",
                    Map.of("key", "profile.featuredProductIds"));
        }
    }

    /** Rutas de las hojas que cambiaron (sin valores: pueden ser correos). */
    private static List<String> changedKeys(Map<String, Object> before, Map<String, Object> after, String prefix) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object previous = before.get(entry.getKey());
            String path = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> && previous instanceof Map<?, ?>) {
                changed.addAll(changedKeys(SettingsSchemas.section(before, entry.getKey()),
                        SettingsSchemas.section(after, entry.getKey()), path + "."));
            } else if (!Objects.equals(previous, entry.getValue())) {
                changed.add(path);
            }
        }
        return changed;
    }
}
