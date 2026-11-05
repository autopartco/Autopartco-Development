package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/**
 * Chequeo común de la analítica del almacén: primero {@code ANALYTICS_READ} (quien no es del almacén no conoce su
 * plan) y después la capacidad {@value #CAPABILITY} del plan.
 */
@Component
@AllArgsConstructor
class StoreAnalyticsAccess {
    static final String CAPABILITY = "ADVANCED_REPORTS";

    private final StoreAccess storeAccess;
    private final PlanEntitlements planEntitlements;

    void require(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ANALYTICS_READ);
        planEntitlements.requireCapability(storeId, CAPABILITY);
    }
}
