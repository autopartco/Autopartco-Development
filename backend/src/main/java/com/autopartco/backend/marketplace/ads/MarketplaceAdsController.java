package com.autopartco.backend.marketplace.ads;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.marketplace.catalog.SearchResponse;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Anuncios {@code CATEGORY_TOP} y {@code PRODUCT_RELATED} (público): {@code GET /marketplace/ads?placement=…}. Límite
 * por IP: 120 por minuto, como {@code /marketplace/home}.
 * <p>
 * {@code Cache-Control: no-store}: cada respuesta lleva tokens de impresión emitidos en esa llamada. Si la entrega
 * falla responde {@code items: []}, nunca 500.
 */
@Slf4j
@RestController
public class MarketplaceAdsController {
    static final String ADS = ApiPaths.MARKETPLACE + "/ads";
    static final String SESSION_HEADER = "X-Session-Id";
    static final int ADS_PER_MINUTE = 120;
    private static final String CATEGORY_TOP = "CATEGORY_TOP";
    private static final String PRODUCT_RELATED = "PRODUCT_RELATED";

    private final SearchService searchService;
    private final SlidingWindowRateLimiter limiter;

    @Autowired
    MarketplaceAdsController(SearchService searchService) {
        this(searchService, Clock.systemUTC());
    }

    MarketplaceAdsController(SearchService searchService, Clock clock) {
        this.searchService = searchService;
        this.limiter = new SlidingWindowRateLimiter("ADS_RATE_LIMITED", ADS_PER_MINUTE, Duration.ofMinutes(1), 20_000,
                clock);
    }

    /** Errores 400: {@code ADS_PLACEMENT_INVALID} (solo CATEGORY_TOP y PRODUCT_RELATED) y {@code ADS_TARGET_REQUIRED}. */
    @GetMapping(ADS)
    public ResponseEntity<AdsResponse> ads(
            HttpServletRequest http,
            @RequestParam(required = false) String placement,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            @RequestHeader(name = SESSION_HEADER, required = false) String sessionId
    ) {
        limiter.acquire(http.getRemoteAddr());
        String requested = placement == null ? "" : placement.strip().toUpperCase(java.util.Locale.ROOT);
        if (!CATEGORY_TOP.equals(requested) && !PRODUCT_RELATED.equals(requested)) {
            throw new BadRequestException("ADS_PLACEMENT_INVALID", "La ubicación debe ser CATEGORY_TOP o PRODUCT_RELATED.");
        }
        if (CATEGORY_TOP.equals(requested) && categoryId == null) {
            throw new BadRequestException("ADS_TARGET_REQUIRED", "CATEGORY_TOP requiere categoryId.");
        }
        if (PRODUCT_RELATED.equals(requested) && productId == null) {
            throw new BadRequestException("ADS_TARGET_REQUIRED", "PRODUCT_RELATED requiere productId.");
        }
        String visitorKey = sessionId != null && !sessionId.isBlank() && sessionId.length() <= 100
                ? "s:" + sessionId.strip()
                : "ip:" + http.getRemoteAddr();
        List<SearchResult.Sponsored> ads;
        try {
            ads = CATEGORY_TOP.equals(requested)
                    ? searchService.categoryAds(categoryId, visitorKey, BuyerContext.anonymous())
                    : searchService.relatedAds(productId, visitorKey, BuyerContext.anonymous());
        } catch (RuntimeException failure) {
            log.warn("Marketplace ads unavailable: {}", failure.getClass().getSimpleName());
            ads = List.of();
        }
        List<SearchResponse.Item> items = ads.stream()
                .map(ad -> new SearchResponse.Item(ad.hit().listing(), ad.hit().distanceKm(), true,
                        ad.adImpressionToken()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AdsResponse(items));
    }

    /** {@code { "items": [...] }}: el mismo ítem que {@code /search} y {@code /home}. */
    public record AdsResponse(List<SearchResponse.Item> items) {
    }
}
