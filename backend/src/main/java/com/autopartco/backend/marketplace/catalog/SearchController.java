package com.autopartco.backend.marketplace.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.MarketplaceSearch;
import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.core.search.SearchInterpretation;
import com.autopartco.backend.core.search.SearchQuery;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.SearchSort;
import com.autopartco.backend.core.search.Suggestions;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Búsqueda v2 del marketplace (público). Contratos en §1 del plan v2.0.0. Límite por IP: 120 búsquedas y 240
 * sugerencias por minuto.
 */
@RestController
public class SearchController {
    static final String SEARCH = ApiPaths.MARKETPLACE + "/search";
    static final String SUGGEST = SEARCH + "/suggest";
    static final int SEARCHES_PER_MINUTE = 120;
    static final int SUGGESTIONS_PER_MINUTE = 240;

    private final SearchService searchService;
    private final Suggestions suggestions;
    private final SearchLog searchLog;
    private final BuyerContexts buyerContexts;
    private final PromotionExposureRecorder exposureRecorder;
    private final BranchService branchService;
    private final SlidingWindowRateLimiter searchLimiter;
    private final SlidingWindowRateLimiter suggestLimiter;

    @Autowired
    public SearchController(SearchService searchService, Suggestions suggestions, SearchLog searchLog,
            BuyerContexts buyerContexts, PromotionExposureRecorder exposureRecorder, BranchService branchService) {
        this(searchService, suggestions, searchLog, buyerContexts, exposureRecorder, branchService,
                Clock.systemUTC());
    }

    SearchController(SearchService searchService, Suggestions suggestions, SearchLog searchLog,
            BuyerContexts buyerContexts, PromotionExposureRecorder exposureRecorder, BranchService branchService,
            Clock clock) {
        this.searchService = searchService;
        this.suggestions = suggestions;
        this.searchLog = searchLog;
        this.buyerContexts = buyerContexts;
        this.exposureRecorder = exposureRecorder;
        this.branchService = branchService;
        this.searchLimiter = new SlidingWindowRateLimiter("SEARCH_RATE_LIMITED", SEARCHES_PER_MINUTE,
                Duration.ofMinutes(1), 20_000, clock);
        this.suggestLimiter = new SlidingWindowRateLimiter("SEARCH_RATE_LIMITED", SUGGESTIONS_PER_MINUTE,
                Duration.ofMinutes(1), 20_000, clock);
    }

    /**
     * Búsqueda con texto libre, filtros, facetas y cercanía. Errores 400: {@code SEARCH_SIZE_INVALID} (1..48),
     * {@code SEARCH_RADIUS_INVALID} (1..200 km), {@code SEARCH_POINT_INCOMPLETE}/{@code SEARCH_POINT_OUTSIDE_COVERAGE}
     * (lat/lng en Colombia), {@code LOCATION_REQUIRED} ({@code sort=nearest} o radio sin ubicación),
     * {@code SEARCH_PRICE_RANGE_INVALID}, {@code SEARCH_SORT_INVALID}, {@code VEHICLE_FILTER_INVALID} y
     * {@code BRANCH_NOT_IN_STORE}. Registra la búsqueda ({@code SearchLog}) y las exposiciones de promociones.
     */
    @GetMapping(SEARCH)
    public SearchResponse search(
            HttpServletRequest http,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(name = "manufacturerId", required = false) List<UUID> manufacturerIds,
            @RequestParam(required = false) UUID makeId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) Integer radiusKm,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Boolean universal,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean facets,
            @RequestParam(required = false) Boolean interpret,
            @RequestHeader(name = ProductBrowseController.SESSION_HEADER, required = false) String sessionId,
            Authentication authentication
    ) {
        searchLimiter.acquire(http.getRemoteAddr());
        if (branchId != null && storeId != null && !branchService.isInStore(storeId, branchId)) {
            throw new BadRequestException("BRANCH_NOT_IN_STORE", "La sucursal no pertenece al almacén indicado.");
        }
        SearchQuery query = SearchQuery.builder()
                .q(q).categoryId(categoryId).manufacturerIds(manufacturerIds)
                .makeId(makeId).modelId(modelId).versionId(versionId).year(year)
                .priceMin(priceMin).priceMax(priceMax).inStock(inStock).onSale(onSale)
                .storeId(storeId).branchId(branchId).cityCode(cityCode).departmentCode(departmentCode)
                .lat(lat).lng(lng).radiusKm(radiusKm).minRating(minRating).universal(universal)
                .sort(SearchSort.fromParameter(sort)).page(page).size(size).facets(facets).interpret(interpret)
                .build();
        BuyerContext buyer = buyerContexts.forBrowsing(authentication);
        SearchResult result = searchService.search(query, buyer);
        SearchInterpretation interpreted = result.interpreted();
        CatalogTracking.recordSearch(searchLog, new MarketplaceSearch(q,
                makeId != null ? makeId : interpreted == null ? null : interpreted.makeId(),
                modelId != null ? modelId : interpreted == null ? null : interpreted.modelId(),
                versionId,
                year != null ? year : interpreted == null ? null : interpreted.year(),
                categoryId,
                cityCode != null ? cityCode : interpreted == null ? null : interpreted.cityCode(),
                (int) Math.min(Integer.MAX_VALUE, result.total()), sessionId,
                CatalogTracking.isAuthenticated(authentication)));
        CatalogTracking.recordExposures(exposureRecorder, buyer,
                result.items().stream().map(SearchResult.Hit::listing).toList());
        return SearchResponse.of(result);
    }

    /**
     * Autocompletado: hasta 8 términos y 4 productos con precio general. Menos de 2 caracteres útiles: listas vacías.
     * Cacheable como pública 5 minutos (no depende de la sesión).
     */
    @GetMapping(SUGGEST)
    public ResponseEntity<Suggestions.Result> suggest(
            HttpServletRequest http,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng
    ) {
        suggestLimiter.acquire(http.getRemoteAddr());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(suggestions.suggest(q, lat, lng));
    }
}
