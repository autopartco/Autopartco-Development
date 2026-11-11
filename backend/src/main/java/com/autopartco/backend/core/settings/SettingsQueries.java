package com.autopartco.backend.core.settings;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreConfigurations;
import com.autopartco.backend.core.settings.internal.SettingsSchemas;
import com.autopartco.backend.shared.cache.TtlCache;

/**
 * Lectura de configuraciones para otros módulos (perfil público, WhatsApp, 2FA del personal). Los ajustes de almacén
 * se cachean 30 s por instancia y se invalidan al confirmar un PATCH.
 */
@Service
public class SettingsQueries {
    private static final long STORE_TTL_SECONDS = 30;

    private final AccountConfigurations accountConfigurations;
    private final StoreConfigurations storeConfigurations;
    private final TtlCache<UUID, StoreSettings> storeCache = new TtlCache<>(STORE_TTL_SECONDS);

    public SettingsQueries(AccountConfigurations accountConfigurations, StoreConfigurations storeConfigurations) {
        this.accountConfigurations = accountConfigurations;
        this.storeConfigurations = storeConfigurations;
    }

    public AccountSettings accountSettings(UUID accountId) {
        return SettingsSchemas.toAccount(SettingsSchemas.ACCOUNT.effective(accountConfigurations.read(accountId)));
    }

    /** Ajustes del almacén (cacheados). {@code STORE_NOT_FOUND} si no existe. */
    public StoreSettings storeSettings(UUID storeId) {
        return storeCache.get(storeId,
                () -> SettingsSchemas.toStore(SettingsSchemas.STORE.effective(storeConfigurations.read(storeId))));
    }

    /** {@code security.requireStaffTwoFactor} del almacén (cacheado). */
    public boolean requiresStaffTwoFactor(UUID storeId) {
        return storeSettings(storeId).security().requireStaffTwoFactor();
    }

    /** Vacía la caché al confirmar la transacción en curso (o ya, si no hay). */
    void invalidateStores() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    storeCache.clear();
                }
            });
        } else {
            storeCache.clear();
        }
    }
}
