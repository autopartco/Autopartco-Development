package com.autopartco.backend.marketplace.home;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.location.ColombiaBounds;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Inicio v2 del marketplace (público): cifras, búsquedas populares, categorías, «Cerca de ti» y «Destacados» en una
 * sola llamada. Límite por IP: 120 por minuto, como {@code /marketplace/search}.
 * <p>
 * {@code Cache-Control: no-store}: la respuesta incluye tokens de anuncio que se emiten en cada llamada para respetar
 * la rotación y el tope de frecuencia; un caché del navegador o de un intermediario los reutilizaría y falsearía las
 * impresiones. La carga a la base de datos la absorbe la caché de 60 s del servidor ({@link HomeCache}).
 */
@RestController
public class HomeController {
    static final String HOME = ApiPaths.MARKETPLACE + "/home";
    static final String SESSION_HEADER = "X-Session-Id";
    static final int HOMES_PER_MINUTE = 120;

    private final HomeService homeService;
    private final SlidingWindowRateLimiter limiter;

    @Autowired
    HomeController(HomeService homeService) {
        this(homeService, Clock.systemUTC());
    }

    HomeController(HomeService homeService, Clock clock) {
        this.homeService = homeService;
        this.limiter = new SlidingWindowRateLimiter("HOME_RATE_LIMITED", HOMES_PER_MINUTE, Duration.ofMinutes(1),
                20_000, clock);
    }

    /**
     * Errores 400: {@code HOME_POINT_INCOMPLETE} (lat y lng juntos o ninguno), {@code HOME_POINT_OUTSIDE_COVERAGE}
     * (fuera de Colombia) y {@code HOME_CITY_INVALID} (DIVIPOLA de 5 dígitos).
     */
    @GetMapping(HOME)
    public ResponseEntity<HomeResponse> home(
            HttpServletRequest http,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(required = false) String cityCode,
            @RequestHeader(name = SESSION_HEADER, required = false) String sessionId
    ) {
        limiter.acquire(http.getRemoteAddr());
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("HOME_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        if (lat != null && !ColombiaBounds.contains(lat, lng)) {
            throw new BadRequestException("HOME_POINT_OUTSIDE_COVERAGE", "La ubicación está fuera de Colombia.");
        }
        if (cityCode != null && !cityCode.isBlank() && !cityCode.strip().matches("\\d{5}")) {
            throw new BadRequestException("HOME_CITY_INVALID", "La ciudad debe tener formato DIVIPOLA (5 dígitos).");
        }
        // Tope de frecuencia de anuncios: sesión anónima del navegador o, sin ella, la IP (solo en memoria).
        String visitorKey = sessionId != null && !sessionId.isBlank() && sessionId.length() <= 100
                ? "s:" + sessionId.strip()
                : "ip:" + http.getRemoteAddr();
        HomeResponse body = homeService.home(HomeArea.of(lat, lng, cityCode), visitorKey);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
