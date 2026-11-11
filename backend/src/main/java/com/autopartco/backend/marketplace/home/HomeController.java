package com.autopartco.backend.marketplace.home;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

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
import com.autopartco.backend.shared.web.AreaCookie;
import com.autopartco.backend.shared.web.RateLimiters;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;
import com.autopartco.backend.shared.web.VisitorCookie;

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

    /** Límite en Redis si está configurado ({@link RateLimiters}); si no, en memoria. */
    @Autowired
    HomeController(HomeService homeService, RateLimiters rateLimiters) {
        this.homeService = homeService;
        this.limiter = rateLimiters.create("home", "HOME_RATE_LIMITED", HOMES_PER_MINUTE, Duration.ofMinutes(1));
    }

    HomeController(HomeService homeService) {
        this(homeService, Clock.systemUTC());
    }

    HomeController(HomeService homeService, Clock clock) {
        this(homeService, RateLimiters.inMemory(clock));
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
        if (lat == null && lng == null) {
            // Área por defecto de la cookie ap_area (lo inválido se ignora). Su ciudad solo si no llegó cityCode.
            Optional<AreaCookie.Area> area = AreaCookie.read(http);
            if (area.isPresent()) {
                lat = area.get().lat();
                lng = area.get().lng();
                if ((cityCode == null || cityCode.isBlank()) && area.get().cityCode() != null) {
                    cityCode = area.get().cityCode();
                }
            }
        }
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("HOME_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        if (lat != null && !ColombiaBounds.contains(lat, lng)) {
            throw new BadRequestException("HOME_POINT_OUTSIDE_COVERAGE", "La ubicación está fuera de Colombia.");
        }
        if (cityCode != null && !cityCode.isBlank() && !cityCode.strip().matches("\\d{5}")) {
            throw new BadRequestException("HOME_CITY_INVALID", "La ciudad debe tener formato DIVIPOLA (5 dígitos).");
        }
        // Tope de frecuencia de anuncios: sesión anónima del navegador, cookie ap_aid o, sin ellas, la IP.
        String visitorKey = VisitorCookie.visitorKey(http, sessionId);
        HomeResponse body = homeService.home(HomeArea.of(lat, lng, cityCode), visitorKey);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
