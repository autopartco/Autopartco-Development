package com.autopartco.backend.storesuite.subscription;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.BillingSettings;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Estado de la suscripción del almacén con uso, capacidades y modo de cobro. Requiere {@code STORE_READ}. */
@Service
@AllArgsConstructor
public class GetSubscription {
    private final StoreAccess storeAccess;
    private final Subscriptions subscriptions;
    private final PlanEntitlements planEntitlements;
    private final BillingSettings billingSettings;

    @Transactional(readOnly = true)
    public SubscriptionResponse execute(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        return assemble(storeId, email);
    }

    /** Arma la respuesta sin comprobar permisos; el llamador ya autorizó a la persona. */
    @Transactional(readOnly = true)
    public SubscriptionResponse assemble(UUID storeId, String email) {
        return SubscriptionResponse.of(
                subscriptions.view(storeId, LocalDateTime.now()),
                planEntitlements.usage(storeId),
                billingSettings.paymentMode(),
                storeAccess.isOwner(email, storeId)
        );
    }
}
