package com.autopartco.backend.storesuite.store;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.settings.SettingsService;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Configuración del almacén ({@code GET/PATCH /stores/{storeId}/settings}). Requiere {@code STORE_UPDATE}; si la cuenta
 * no es (ni fue) del almacén responde 404. Está exenta de {@link StaffTwoFactorInterceptor} para que se pueda
 * leer; el PATCH de quien no tiene 2FA, si el almacén la exige, lo rechaza {@code SettingsService.patchStore}.
 */
@Service
@AllArgsConstructor
public class ManageStoreSettings {
    private final StoreAccess storeAccess;
    private final AccountConfigurations accountConfigurations;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public StoreSettings get(UUID storeId, String email) {
        authorize(storeId, email);
        return settingsService.storeSettings(storeId);
    }

    @Transactional
    public StoreSettings patch(UUID storeId, String email, Map<String, Object> patch) {
        AccountConfigurations.SecurityStatus actor = authorize(storeId, email);
        return settingsService.patchStore(storeId, actor.accountId(), actor.twoFactorEnabled(), patch);
    }

    private AccountConfigurations.SecurityStatus authorize(UUID storeId, String email) {
        AccountConfigurations.SecurityStatus actor = accountConfigurations.securityStatus(email)
                .filter(status -> storeAccess.isMember(status.personId(), storeId))
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
        return actor;
    }
}
