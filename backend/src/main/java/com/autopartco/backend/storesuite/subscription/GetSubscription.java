package com.autopartco.backend.storesuite.subscription;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.SubscriptionView;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Estado de la suscripción del almacén. Requiere {@code STORE_READ}. */
@Service
@AllArgsConstructor
public class GetSubscription {
    private final StoreAccess storeAccess;
    private final Subscriptions subscriptions;

    @Transactional(readOnly = true)
    public SubscriptionView execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return subscriptions.view(storeId, LocalDateTime.now());
    }
}
