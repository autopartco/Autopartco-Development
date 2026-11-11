package com.autopartco.backend.storesuite.advertising;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.advertising.AdCampaignRequest;
import com.autopartco.backend.core.advertising.AdCampaignView;
import com.autopartco.backend.core.advertising.AdCheckoutResponse;
import com.autopartco.backend.core.advertising.AdPriceView;
import com.autopartco.backend.core.advertising.AdStats;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/** Publicidad del almacén (§2 del plan v2.0.0). Permisos y errores en {@link StoreAds}. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/ads")
@AllArgsConstructor
public class StoreAdsController {
    private final StoreAds storeAds;

    /** Cuerpo de {@code POST …/payment/sync}: id de la transacción que Wompi devuelve en la redirección. */
    public record PaymentSyncRequest(@NotBlank @Size(max = 64) String transactionId) {
    }

    @GetMapping("/prices")
    public List<AdPriceView> prices(@PathVariable UUID storeId, Authentication authentication) {
        return storeAds.prices(storeId, authentication.getName());
    }

    @GetMapping("/campaigns")
    public List<AdCampaignView> list(@PathVariable UUID storeId, @RequestParam(required = false) String status,
            Authentication authentication) {
        return storeAds.list(storeId, authentication.getName(), status);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<AdCampaignView> create(@PathVariable UUID storeId,
            @Valid @RequestBody AdCampaignRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeAds.create(storeId, authentication.getName(), request));
    }

    @GetMapping("/campaigns/{campaignId}")
    public AdCampaignView get(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        return storeAds.get(storeId, campaignId, authentication.getName());
    }

    @PutMapping("/campaigns/{campaignId}")
    public AdCampaignView update(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            @Valid @RequestBody AdCampaignRequest request, Authentication authentication) {
        return storeAds.update(storeId, campaignId, authentication.getName(), request);
    }

    @DeleteMapping("/campaigns/{campaignId}")
    public ResponseEntity<Void> delete(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        storeAds.delete(storeId, campaignId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/campaigns/{campaignId}/checkout")
    public AdCheckoutResponse checkout(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        return storeAds.checkout(storeId, campaignId, authentication.getName());
    }

    @PostMapping("/campaigns/{campaignId}/payment/sync")
    public AdCampaignView syncPayment(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            @Valid @RequestBody PaymentSyncRequest request, Authentication authentication) {
        return storeAds.syncPayment(storeId, campaignId, authentication.getName(), request.transactionId());
    }

    @PostMapping("/campaigns/{campaignId}/pause")
    public AdCampaignView pause(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        return storeAds.pause(storeId, campaignId, authentication.getName());
    }

    @PostMapping("/campaigns/{campaignId}/resume")
    public AdCampaignView resume(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        return storeAds.resume(storeId, campaignId, authentication.getName());
    }

    @PostMapping("/campaigns/{campaignId}/cancel")
    public AdCampaignView cancel(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            Authentication authentication) {
        return storeAds.cancel(storeId, campaignId, authentication.getName());
    }

    @GetMapping("/campaigns/{campaignId}/stats")
    public AdStats stats(@PathVariable UUID storeId, @PathVariable UUID campaignId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            Authentication authentication) {
        return storeAds.stats(storeId, campaignId, authentication.getName(), from, to);
    }
}
