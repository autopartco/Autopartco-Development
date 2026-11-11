package com.autopartco.backend.storesuite.advertising;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.autopartco.backend.core.advertising.AdCampaignRequest;
import com.autopartco.backend.core.advertising.AdCampaignStatus;
import com.autopartco.backend.core.advertising.AdCampaignView;
import com.autopartco.backend.core.advertising.AdCampaigns;
import com.autopartco.backend.core.advertising.AdCheckout;
import com.autopartco.backend.core.advertising.AdCheckoutResponse;
import com.autopartco.backend.core.advertising.AdPriceView;
import com.autopartco.backend.core.advertising.AdStats;
import com.autopartco.backend.core.advertising.AdStatsQueries;
import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

import lombok.AllArgsConstructor;

/**
 * Casos de uso de publicidad del almacén. Permisos por código, como el resto de {@code storesuite}:
 * <ul>
 *   <li>Escribir (crear, editar, borrar, pagar, pausar, reanudar, cancelar): {@code STORE_UPDATE}.</li>
 *   <li>Estadísticas: {@code ANALYTICS_READ}.</li>
 *   <li>Leer tarifas y campañas: {@code STORE_UPDATE} o {@code ANALYTICS_READ}.</li>
 * </ul>
 * Crear, editar, pagar y reanudar exigen la suscripción activa (409 {@code SUBSCRIPTION_INACTIVE}). Una campaña de
 * otro almacén responde 404 {@code AD_CAMPAIGN_NOT_FOUND}.
 */
@Service
@AllArgsConstructor
public class StoreAds {
    private final StoreAccess storeAccess;
    private final AccountLookup accountLookup;
    private final PlanEntitlements planEntitlements;
    private final AdCampaigns campaigns;
    private final AdCheckout checkout;
    private final AdStatsQueries statsQueries;

    public List<AdPriceView> prices(UUID storeId, String email) {
        requireRead(storeId, email);
        return campaigns.prices(true);
    }

    public AdCampaignView create(UUID storeId, String email, AdCampaignRequest request) {
        requireWrite(storeId, email);
        requireSubscription(storeId);
        return campaigns.create(storeId, accountLookup.requireByEmail(email).accountId(), request);
    }

    public AdCampaignView update(UUID storeId, UUID campaignId, String email, AdCampaignRequest request) {
        requireWrite(storeId, email);
        requireSubscription(storeId);
        return campaigns.update(storeId, campaignId, request);
    }

    public void delete(UUID storeId, UUID campaignId, String email) {
        requireWrite(storeId, email);
        campaigns.delete(storeId, campaignId);
    }

    public List<AdCampaignView> list(UUID storeId, String email, String status) {
        requireRead(storeId, email);
        AdCampaignStatus parsed = null;
        if (status != null && !status.isBlank()) {
            parsed = AdCampaignStatus.parse(status).orElseThrow(() -> new BadRequestException(
                    "AD_CAMPAIGN_STATUS_INVALID", "Estado de campaña desconocido."));
        }
        return campaigns.list(storeId, parsed);
    }

    public AdCampaignView get(UUID storeId, UUID campaignId, String email) {
        requireRead(storeId, email);
        return campaigns.get(storeId, campaignId);
    }

    public AdCheckoutResponse checkout(UUID storeId, UUID campaignId, String email) {
        requireWrite(storeId, email);
        requireSubscription(storeId);
        return checkout.checkout(storeId, campaignId, accountLookup.requireByEmail(email).accountId());
    }

    public AdCampaignView syncPayment(UUID storeId, UUID campaignId, String email, String transactionId) {
        requireWrite(storeId, email);
        return checkout.sync(storeId, campaignId, transactionId, email);
    }

    public AdCampaignView pause(UUID storeId, UUID campaignId, String email) {
        requireWrite(storeId, email);
        return campaigns.pause(storeId, campaignId);
    }

    public AdCampaignView resume(UUID storeId, UUID campaignId, String email) {
        requireWrite(storeId, email);
        requireSubscription(storeId);
        return campaigns.resume(storeId, campaignId);
    }

    public AdCampaignView cancel(UUID storeId, UUID campaignId, String email) {
        requireWrite(storeId, email);
        return campaigns.cancel(storeId, campaignId);
    }

    public AdStats stats(UUID storeId, UUID campaignId, String email, String from, String to) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ANALYTICS_READ);
        return statsQueries.stats(storeId, campaignId, from, to);
    }

    private void requireWrite(UUID storeId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.STORE_UPDATE);
    }

    private void requireRead(UUID storeId, String email) {
        List<String> permissions = storeAccess.permissionsFor(email, storeId);
        if (!permissions.contains(StorePermissionCodes.STORE_UPDATE)
                && !permissions.contains(StorePermissionCodes.ANALYTICS_READ)) {
            throw new AccessDeniedException("Store permission denied: STORE_UPDATE or ANALYTICS_READ");
        }
    }

    private void requireSubscription(UUID storeId) {
        if (!planEntitlements.isSubscriptionActive(storeId)) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE",
                    "Necesitas una suscripción activa para anunciar productos.");
        }
    }
}
