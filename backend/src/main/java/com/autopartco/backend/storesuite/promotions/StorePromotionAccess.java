package com.autopartco.backend.storesuite.promotions;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionService;

import lombok.AllArgsConstructor;

/**
 * Chequeo común de los casos de uso de promociones del almacén: permiso por código y existencia del almacén. Como
 * antes, si el almacén no existe responde {@code PROMOTION_NOT_FOUND} (no {@code STORE_NOT_FOUND}).
 */
@Component
@AllArgsConstructor
class StorePromotionAccess {
    static final String PROMOTIONS_CAPABILITY = "PROMOTIONS";

    private final StoreAccess storeAccess;
    private final StoreService storeService;

    void requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccess.requirePermission(email, storeId, permissionCode);
        if (!storeService.exists(storeId)) {
            throw PromotionService.notFound();
        }
    }
}
