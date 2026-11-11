package com.autopartco.backend.operations.ads;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.advertising.AdCampaignStatus;
import com.autopartco.backend.core.advertising.AdCampaignView;
import com.autopartco.backend.core.advertising.AdCampaigns;
import com.autopartco.backend.core.advertising.AdPriceUpdate;
import com.autopartco.backend.core.advertising.AdPriceView;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/** Moderación de campañas y tarifas de publicidad (solo root). Cada cambio queda en la auditoría de admin. */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/ads")
@AllArgsConstructor
public class AdAdminController {
    private final AdCampaigns campaigns;
    private final AdminAudit adminAudit;

    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record PricesRequest(@NotEmpty @Size(max = 4) List<@Valid AdPriceUpdate> prices) {
    }

    /** Las {@value AdCampaigns#ADMIN_LIST_LIMIT} campañas más recientes, con filtros opcionales. */
    @GetMapping("/campaigns")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<AdCampaignView> campaigns(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID storeId) {
        AdCampaignStatus parsed = null;
        if (status != null && !status.isBlank()) {
            parsed = AdCampaignStatus.parse(status).orElseThrow(() -> new BadRequestException(
                    "AD_CAMPAIGN_STATUS_INVALID", "Estado de campaña desconocido."));
        }
        return campaigns.adminList(parsed, storeId);
    }

    /** Rechaza; si estaba pagada y no había empezado, queda con reembolso manual pendiente. */
    @PostMapping("/campaigns/{id}/reject")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdCampaignView reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request,
            Authentication authentication) {
        AdCampaignView campaign = campaigns.reject(id, request.reason());
        adminAudit.record(authentication, "AD_CAMPAIGN_REJECTED", "AD_CAMPAIGN", id, Map.of(
                "storeId", campaign.storeId(),
                "reason", campaign.rejectedReason(),
                "requiresManualRefund", campaign.requiresManualRefund()));
        return campaign;
    }

    @PostMapping("/campaigns/{id}/pause")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdCampaignView pause(@PathVariable UUID id, Authentication authentication) {
        AdCampaignView campaign = campaigns.adminPause(id);
        adminAudit.record(authentication, "AD_CAMPAIGN_PAUSED", "AD_CAMPAIGN", id,
                Map.of("storeId", campaign.storeId()));
        return campaign;
    }

    @GetMapping("/prices")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<AdPriceView> prices() {
        return campaigns.prices(false);
    }

    /** Fija tarifas diarias (COP, IVA incluido). Body: {@code {"prices":[{placement, dailyPrice, active}]}}. */
    @PutMapping("/prices")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<AdPriceView> updatePrices(@Valid @RequestBody PricesRequest request, Authentication authentication) {
        List<AdPriceView> prices = campaigns.updatePrices(request.prices());
        Map<String, Object> metadata = new LinkedHashMap<>();
        request.prices().forEach(price -> metadata.put(price.placement().name(),
                price.dailyPrice().toPlainString() + (Boolean.FALSE.equals(price.active()) ? " (inactiva)" : "")));
        adminAudit.record(authentication, "AD_PRICES_UPDATED", "AD_PLACEMENT_PRICE", null, metadata);
        return prices;
    }
}
