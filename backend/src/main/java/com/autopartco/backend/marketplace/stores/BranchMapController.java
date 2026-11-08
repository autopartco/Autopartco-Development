package com.autopartco.backend.marketplace.stores;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.BranchMapQueries;
import com.autopartco.backend.core.organization.MapBranchView;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.SlidingWindowRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/** Sedes de almacenes publicados para el mapa de cobertura (público, solo Colombia). Límite: 60 por IP y minuto. */
@RestController
@RequestMapping(ApiPaths.MARKETPLACE + "/branches")
public class BranchMapController {
    static final BigDecimal MIN_LAT = new BigDecimal("-4.3");
    static final BigDecimal MAX_LAT = new BigDecimal("13.5");
    static final BigDecimal MIN_LNG = new BigDecimal("-82");
    static final BigDecimal MAX_LNG = new BigDecimal("-66.8");
    static final int DEFAULT_LIMIT = 4;
    static final int MAX_LIMIT = 8;

    private final BranchMapQueries branchMapQueries;
    private final SlidingWindowRateLimiter rateLimiter;

    @Autowired
    public BranchMapController(BranchMapQueries branchMapQueries) {
        this(branchMapQueries, Clock.systemUTC());
    }

    BranchMapController(BranchMapQueries branchMapQueries, Clock clock) {
        this.branchMapQueries = branchMapQueries;
        this.rateLimiter = new SlidingWindowRateLimiter("MAP_RATE_LIMITED", 60, Duration.ofMinutes(1), 20_000, clock);
    }

    // Los límites son opcionales a nivel de Spring para responder 400 con código propio (el handler genérico daría 500).
    @GetMapping("/map")
    public List<MapBranchView> map(HttpServletRequest http, @RequestParam(required = false) BigDecimal south,
            @RequestParam(required = false) BigDecimal west, @RequestParam(required = false) BigDecimal north,
            @RequestParam(required = false) BigDecimal east, @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        rateLimiter.acquire(http.getRemoteAddr());
        if (south == null || west == null || north == null || east == null) {
            throw new BadRequestException("MAP_BOUNDS_REQUIRED", "Envía south, west, north y east.");
        }
        if ((lat == null) != (lng == null)) {
            throw new BadRequestException("MAP_POINT_INCOMPLETE", "Envía lat y lng juntos o ninguno.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("MAP_LIMIT_INVALID", "El límite debe estar entre 1 y " + MAX_LIMIT + ".");
        }
        BigDecimal clippedSouth = south.max(MIN_LAT);
        BigDecimal clippedNorth = north.min(MAX_LAT);
        BigDecimal clippedWest = west.max(MIN_LNG);
        BigDecimal clippedEast = east.min(MAX_LNG);
        if (clippedSouth.compareTo(clippedNorth) > 0 || clippedWest.compareTo(clippedEast) > 0) {
            throw new BadRequestException("MAP_AREA_OUTSIDE_COVERAGE", "El área está fuera de Colombia.");
        }
        return branchMapQueries.find(clippedSouth, clippedWest, clippedNorth, clippedEast, lat, lng, limit);
    }
}
