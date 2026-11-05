package com.autopartco.backend.operations.metrics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.Analytics;
import com.autopartco.backend.core.analytics.CustomerSegment;
import com.autopartco.backend.core.analytics.CustomerSegments;
import com.autopartco.backend.core.analytics.DateRange;
import com.autopartco.backend.core.analytics.PlatformCustomerReport;
import com.autopartco.backend.core.analytics.SearchInsights;
import com.autopartco.backend.core.analytics.SearchReport;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * Inteligencia comercial de la plataforma (solo root): clientes segmentados con consentimiento de marketing y
 * demanda de búsquedas del marketplace.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/analytics")
@AllArgsConstructor
public class PlatformAnalyticsController {
    private final CustomerSegments customerSegments;
    private final SearchInsights searchInsights;

    /** Página desde 1; tamaño por defecto 50 y como máximo 200. Segmento desconocido: 400 {@code INVALID_SEGMENT}. */
    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PlatformCustomerReport customers(
            @RequestParam(required = false) String segment,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + CustomerSegments.DEFAULT_PAGE_SIZE) int size
    ) {
        return customerSegments.platformCustomers(CustomerSegment.parse(segment), page, size);
    }

    /** Rango por defecto: últimos 30 días; máximo 366 (si no, 400 {@code INVALID_RANGE}). */
    @GetMapping("/searches")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public SearchReport searches(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return searchInsights.report(DateRange.resolve(from, to, Analytics.today()));
    }
}
