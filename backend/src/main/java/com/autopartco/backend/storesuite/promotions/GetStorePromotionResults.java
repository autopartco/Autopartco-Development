package com.autopartco.backend.storesuite.promotions;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.analytics.PromotionResult;
import com.autopartco.backend.core.analytics.PromotionResults;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.pricing.PromotionResponse;
import com.autopartco.backend.core.pricing.PromotionService;

import lombok.AllArgsConstructor;

/**
 * Resultados de una promoción del almacén (alcance, ventas atribuidas y efecto frente al grupo de control). Exige
 * {@code PROMOTION_READ} y la capacidad {@code PROMOTIONS}; {@code PROMOTION_NOT_FOUND} si la promoción no es del
 * almacén. Solo agregados: nunca identidades de compradores.
 */
@Service
@AllArgsConstructor
public class GetStorePromotionResults {
    private final PlanEntitlements planEntitlements;
    private final StorePromotionAccess access;
    private final PromotionService promotionService;
    private final PromotionResults promotionResults;

    @Transactional(readOnly = true)
    public PromotionResult execute(UUID storeId, UUID promotionId, String email) {
        access.requirePermission(storeId, email, StorePermissionCodes.PROMOTION_READ);
        planEntitlements.requireCapability(storeId, StorePromotionAccess.PROMOTIONS_CAPABILITY);
        PromotionResponse promotion = promotionService.getForStore(storeId, promotionId);
        return promotionResults.results(
                promotion.id(), storeId, promotion.startsAt(), promotion.endsAt(), promotion.audience().name());
    }
}
