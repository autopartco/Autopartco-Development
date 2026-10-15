package com.autopartco.backend.storesuite.promotions;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.pricing.PromotionService;

import lombok.AllArgsConstructor;

/** Borra una promoción del almacén. Requiere {@code PROMOTION_DELETE} (sin exigir la capacidad del plan). */
@Service
@AllArgsConstructor
public class DeleteStorePromotion {
    private final StorePromotionAccess access;
    private final PromotionService promotionService;

    @Transactional
    public void execute(UUID storeId, UUID promotionId, String email) {
        access.requirePermission(storeId, email, StorePermissionCodes.PROMOTION_DELETE);
        promotionService.deleteForStore(storeId, promotionId);
    }
}
