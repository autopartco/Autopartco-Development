package com.autopartco.backend.storesuite.subscription;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.SubscriptionPaymentView;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Historial de pagos de la suscripción. Requiere {@code STORE_READ}. */
@Service
@AllArgsConstructor
public class ListSubscriptionPayments {
    private final StoreAccess storeAccess;
    private final Subscriptions subscriptions;

    @Transactional(readOnly = true)
    public List<SubscriptionPaymentView> execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return subscriptions.payments(storeId);
    }
}
