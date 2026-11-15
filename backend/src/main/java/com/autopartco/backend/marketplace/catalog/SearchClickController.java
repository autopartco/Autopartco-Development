package com.autopartco.backend.marketplace.catalog;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.SearchClickLog;
import com.autopartco.backend.shared.web.ClientIp;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Clic en un resultado de búsqueda (público, comprador anónimo incluido): {@code POST /marketplace/search/click} con
 * {@code {searchId, productId, position}}. Responde 204 siempre que el cuerpo sea válido: la escritura es asíncrona
 * ({@link SearchClickLog}) y un clic de una búsqueda o producto inexistente se descarta sin error. Límite por IP: 120
 * por minuto, con el mismo mecanismo que {@code /marketplace/search} ({@link RateLimiters}); al pasarlo, 429
 * {@code SEARCH_CLICK_RATE_LIMITED}. No recibe ni guarda datos personales.
 */
@RestController
public class SearchClickController {
    static final String CLICK = SearchController.SEARCH + "/click";
    static final int CLICKS_PER_MINUTE = 120;

    private final SearchClickLog clickLog;
    private final SlidingWindowRateLimiter limiter;

    /** Límite en Redis si está configurado ({@link RateLimiters}); si no, en memoria. */
    @Autowired
    public SearchClickController(SearchClickLog clickLog, RateLimiters rateLimiters) {
        this.clickLog = clickLog;
        this.limiter = rateLimiters.create("search-click", "SEARCH_CLICK_RATE_LIMITED", CLICKS_PER_MINUTE,
                Duration.ofMinutes(1));
    }

    SearchClickController(SearchClickLog clickLog, Clock clock) {
        this(clickLog, RateLimiters.inMemory(clock));
    }

    @PostMapping(CLICK)
    public ResponseEntity<Void> click(@Valid @RequestBody ClickRequest request, HttpServletRequest http) {
        limiter.acquire(ClientIp.of(http));
        clickLog.record(request.searchId(), request.productId(), request.position());
        return ResponseEntity.noContent().build();
    }

    /** {@code searchId} es el de la respuesta de {@code /search}; {@code position} desde 0 en la lista mostrada. */
    public record ClickRequest(
            @NotNull UUID searchId,
            @NotNull UUID productId,
            @NotNull @Min(0) Integer position
    ) {
    }
}
