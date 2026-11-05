package com.autopartco.backend.storesuite.promotions;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionService;
import com.autopartco.backend.core.pricing.PromotionUpsertRequest;

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

    /**
     * {@code true} si el almacén puede dirigir la promoción a {@code PLATFORM_FREQUENT}: su plan efectivo incluye
     * {@value PromotionService#PLATFORM_AUDIENCE_CAPABILITY} (sin suscripción registrada, mismo criterio que
     * {@code requireCapability}: se permite). Solo consulta el plan si la solicitud pide esa audiencia. Se llama
     * después de {@code requireCapability(PROMOTIONS)}, que ya rechazó solicitudes inactivas o vencidas.
     */
    static boolean platformAudienceAllowed(
            PlanEntitlements planEntitlements,
            UUID storeId,
            PromotionUpsertRequest request
    ) {
        if (request == null || request.audienceOrDefault() != PromotionAudience.PLATFORM_FREQUENT) {
            return false;
        }
        return planEntitlements.accessForStore(storeId)
                .map(access -> !access.subscriptionExpired()
                        && access.capabilities().contains(PromotionService.PLATFORM_AUDIENCE_CAPABILITY))
                .orElse(true);
    }
}
