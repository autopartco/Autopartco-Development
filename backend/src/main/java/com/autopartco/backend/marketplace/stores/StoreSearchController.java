package com.autopartco.backend.marketplace.stores;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.search.StoreSearchItem;
import com.autopartco.backend.core.search.StoreSearchQuery;
import com.autopartco.backend.core.search.StoreSearchResult;
import com.autopartco.backend.core.search.StoreSearchService;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.ClientIp;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Búsqueda pública de almacenes publicados. Límite por IP: 120 peticiones por minuto (como
 * {@code /marketplace/search}). El mapeo literal {@code /search} tiene prioridad sobre {@code /{storeId}} de
 * {@link StoreBrowseController}.
 */
@RestController
public class StoreSearchController {
    static final String SEARCH = ApiPaths.MARKETPLACE + "/stores/search";
    static final int REQUESTS_PER_MINUTE = 120;

    /** {@code page} desde 0. */
    public record Response(List<StoreSearchItem> items, int page, int size, long total) {
        static Response of(StoreSearchResult result) {
            return new Response(result.items(), result.page(), result.size(), result.total());
        }
    }

    private final StoreSearchService searchService;
    private final SlidingWindowRateLimiter limiter;

    /** Límite en Redis si está configurado ({@link RateLimiters}); si no, en memoria. */
    @Autowired
    public StoreSearchController(StoreSearchService searchService, RateLimiters rateLimiters) {
        this.searchService = searchService;
        this.limiter = rateLimiters.create("store-search", "SEARCH_RATE_LIMITED", REQUESTS_PER_MINUTE,
                Duration.ofMinutes(1));
    }

    /** Pruebas. */
    StoreSearchController(StoreSearchService searchService, Clock clock) {
        this(searchService, RateLimiters.inMemory(clock));
    }

    /**
     * Almacenes publicados cuyo nombre, descripción o ciudad de una sucursal activa coincide con {@code q} (sin tildes
     * ni mayúsculas; sin {@code q}, todos por calificación). {@code cityCode} (DIVIPOLA, 5 dígitos) filtra por municipio
     * de una sucursal activa. Errores 400: {@code SEARCH_PLACE_INVALID}, {@code SEARCH_SIZE_INVALID} (1..24) y
     * {@code SEARCH_PAGE_INVALID}; 429 {@code SEARCH_RATE_LIMITED}.
     */
    @GetMapping(SEARCH)
    public Response search(
            HttpServletRequest http,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cityCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        limiter.acquire(ClientIp.of(http));
        StoreSearchQuery query = new StoreSearchQuery(q, cityCode, page, size);
        query.validate();
        return Response.of(searchService.search(query));
    }
}
