package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.advertising.internal.AdCampaign;

/**
 * Campaña para el portal y el admin ({@code AdCampaign} de §2). {@code totalPrice} en COP (IVA incluido), calculado
 * por el servidor. {@code requiresManualRefund}: pago aprobado que no se pudo aplicar o campaña rechazada antes de
 * empezar; el reembolso es manual. {@code pausedBy}: {@code STORE} o {@code ADMIN} solo en {@code PAUSED} (el almacén
 * no puede reanudar una pausa del admin); nulo en los demás estados.
 */
public record AdCampaignView(
        UUID id,
        UUID storeId,
        String name,
        AdCampaignStatus status,
        AdPausedBy pausedBy,
        List<AdPlacement> placements,
        List<UUID> productIds,
        LocalDate startsOn,
        LocalDate endsOn,
        long days,
        AdTargeting targeting,
        BigDecimal totalPrice,
        LocalDateTime paidAt,
        String rejectedReason,
        boolean requiresManualRefund,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static AdCampaignView of(AdCampaign campaign) {
        return new AdCampaignView(
                campaign.getId(),
                campaign.getStoreId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getPausedBy(),
                campaign.placementList(),
                List.copyOf(campaign.getProductIds()),
                campaign.getStartsOn(),
                campaign.getEndsOn(),
                AdCampaignRules.days(campaign.getStartsOn(), campaign.getEndsOn()),
                campaign.targetingValue(),
                campaign.getTotalPrice(),
                campaign.getPaidAt(),
                campaign.getRejectedReason(),
                campaign.isRequiresManualRefund(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }
}
