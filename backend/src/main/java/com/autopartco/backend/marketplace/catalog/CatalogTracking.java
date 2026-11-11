package com.autopartco.backend.marketplace.catalog;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionAudience;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;

/** Registro best-effort de búsquedas y exposiciones de promociones del catálogo; nunca afecta la respuesta. */
final class CatalogTracking {
    private CatalogTracking() {
    }

    /**
     * Registra en {@code promotion_exposures} los precios mostrados que ganó una promoción con audiencia distinta de
     * {@code ALL}. Solo compradores autenticados.
     */
    static void recordExposures(PromotionExposureRecorder recorder, BuyerContext buyer,
            List<ProductResponse> listings) {
        if (buyer == null || buyer.isAnonymous() || listings == null) {
            return;
        }
        try {
            List<PromotionExposureRecorder.Exposure> exposures = listings.stream()
                    .filter(listing -> listing.appliedPromotionId() != null
                            && listing.appliedPromotionAudience() != null
                            && listing.appliedPromotionAudience() != PromotionAudience.ALL)
                    .map(listing -> new PromotionExposureRecorder.Exposure(
                            listing.appliedPromotionId(), listing.id()))
                    .distinct()
                    .toList();
            recorder.record(buyer.personId(), exposures);
        } catch (RuntimeException ignored) {
            // Best-effort: el registro de exposiciones nunca rompe el catálogo.
        }
    }

    static void recordSearch(SearchLog searchLog, MarketplaceSearch search) {
        try {
            searchLog.record(search);
        } catch (RuntimeException ignored) {
            // Best-effort: el registro de búsquedas nunca rompe el catálogo.
        }
    }

    static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
