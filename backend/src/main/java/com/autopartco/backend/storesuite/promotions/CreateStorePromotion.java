package com.autopartco.backend.storesuite.promotions;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;

import lombok.AllArgsConstructor;

/**
 * Crea una promoción del almacén. Primero exige la capacidad {@code PROMOTIONS} del plan y después
 * {@code PROMOTION_CREATE} (orden heredado).
 */
@Service
@AllArgsConstructor
public class CreateStorePromotion {
    private final PlanEntitlements planEntitlements;
    private final StorePromotionAccess access;
    private final PromotionService promotionService;

    @Transactional
    public PromotionResponse execute(UUID storeId, String email, PromotionUpsertRequest request) {
        planEntitlements.requireCapability(storeId, StorePromotionAccess.PROMOTIONS_CAPABILITY);
        access.requirePermission(storeId, email, StorePermissionCodes.PROMOTION_CREATE);
        return promotionService.createForStore(storeId, request);
    }
}
