package com.autopartco.backend.core.settings;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreConfigurations;
import com.autopartco.backend.core.settings.internal.SettingsSchemas;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;

/**
 * Lectura de configuraciones para otros módulos (perfil público, WhatsApp, 2FA del personal). Los ajustes de almacén
 * van en la caché {@code settings:store} (5 min; compartida en Redis si está configurado) y la entrada del almacén se
 * invalida al confirmar un PATCH (en Redis, para todas las instancias).
 */
@Service
public class SettingsQueries {
    private final AccountConfigurations accountConfigurations;
    private final StoreConfigurations storeConfigurations;
    private final TypedCache<StoreSettings> storeCache;

    @Autowired
    public SettingsQueries(AccountConfigurations accountConfigurations, StoreConfigurations storeConfigurations,
            ResilientCacheManager caches) {
        this.accountConfigurations = accountConfigurations;
        this.storeConfigurations = storeConfigurations;
        this.storeCache = (caches == null ? ResilientCacheManager.local() : caches)
                .typed(AppCaches.SETTINGS_STORE, StoreSettings.class);
    }

    /** Con caché local propia (pruebas). */
    public SettingsQueries(AccountConfigurations accountConfigurations, StoreConfigurations storeConfigurations) {
        this(accountConfigurations, storeConfigurations, null);
    }

    public AccountSettings accountSettings(UUID accountId) {
        return SettingsSchemas.toAccount(SettingsSchemas.ACCOUNT.effective(accountConfigurations.read(accountId)));
    }

    /** Ajustes del almacén (cacheados). {@code STORE_NOT_FOUND} si no existe. */
    public StoreSettings storeSettings(UUID storeId) {
        return storeCache.get(storeId.toString(),
                () -> SettingsSchemas.toStore(SettingsSchemas.STORE.effective(storeConfigurations.read(storeId))));
    }

    /** {@code security.requireStaffTwoFactor} del almacén (cacheado). */
    public boolean requiresStaffTwoFactor(UUID storeId) {
        return storeSettings(storeId).security().requireStaffTwoFactor();
    }

    /** Descarta la entrada del almacén al confirmar la transacción en curso (o ya, si no hay). */
    void invalidateStore(UUID storeId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    storeCache.evict(storeId.toString());
                }
            });
        } else {
            storeCache.evict(storeId.toString());
        }
    }
}
