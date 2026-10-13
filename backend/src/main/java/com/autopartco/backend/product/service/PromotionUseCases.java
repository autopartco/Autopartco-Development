package com.autopartco.backend.product.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;

import lombok.AllArgsConstructor;

/**
 * Temporal (fase 4a): permiso y capacidad {@code PROMOTIONS} antes de {@code core.pricing.PromotionService}. En la
 * fase 4b pasa a {@code storesuite.promotions} y {@code operations.promotions}.
 */
@Service
@AllArgsConstructor
public class PromotionUseCases {
    private static final String PROMOTIONS = "PROMOTIONS";

    private final PromotionService promotionService;
    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final PlanEntitlements entitlements;

    @Transactional(readOnly = true)
    public List<PromotionResponse> listForStore(UUID storeId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_READ);
        return promotionService.listForStore(storeId);
    }

    @Transactional
    public PromotionResponse createForStore(UUID storeId, String email, PromotionUpsertRequest request) {
        entitlements.requireCapability(storeId, PROMOTIONS);
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_CREATE);
        return promotionService.createForStore(storeId, request);
    }

    @Transactional
    public PromotionResponse updateForStore(
            UUID storeId,
            UUID promotionId,
            String email,
            PromotionUpsertRequest request
    ) {
        entitlements.requireCapability(storeId, PROMOTIONS);
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_UPDATE);
        return promotionService.updateForStore(storeId, promotionId, request);
    }

    @Transactional
    public void deleteForStore(UUID storeId, UUID promotionId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PROMOTION_DELETE);
        promotionService.deleteForStore(storeId, promotionId);
    }

    public List<PromotionResponse> listForPlatform() {
        return promotionService.listForPlatform();
    }

    public PromotionResponse createForPlatform(PromotionUpsertRequest request) {
        return promotionService.createForPlatform(request);
    }

    public PromotionResponse updateForPlatform(UUID promotionId, PromotionUpsertRequest request) {
        return promotionService.updateForPlatform(promotionId, request);
    }

    public void deleteForPlatform(UUID promotionId) {
        promotionService.deleteForPlatform(promotionId);
    }

    /** Como antes: sin almacén, {@code PROMOTION_NOT_FOUND} (no {@code STORE_NOT_FOUND}). */
    private void requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccess.requirePermission(email, storeId, permissionCode);
        if (!storeService.exists(storeId)) {
            throw PromotionService.notFound();
        }
    }
}
