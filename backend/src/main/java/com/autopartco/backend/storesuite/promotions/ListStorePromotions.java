package com.autopartco.backend.storesuite.promotions;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionService;

import lombok.AllArgsConstructor;

/** Lista las promociones del almacén. Requiere {@code PROMOTION_READ} (sin exigir la capacidad del plan). */
@Service
@AllArgsConstructor
public class ListStorePromotions {
    private final StorePromotionAccess access;
    private final PromotionService promotionService;

    @Transactional(readOnly = true)
    public List<PromotionResponse> execute(UUID storeId, String email) {
        access.requirePermission(storeId, email, StorePermissionCodes.PROMOTION_READ);
        return promotionService.listForStore(storeId);
    }
}
