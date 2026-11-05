package com.autopartco.backend.storesuite.analytics;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.CustomerReport;
import com.autopartco.backend.core.analytics.ProductReport;
import com.autopartco.backend.core.analytics.SalesForecast;
import com.autopartco.backend.core.analytics.SalesReport;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Inteligencia comercial del almacén. Requiere {@code ANALYTICS_READ} y la capacidad {@code ADVANCED_REPORTS}. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/analytics")
@AllArgsConstructor
public class AnalyticsController {
    private final GetSalesReport getSalesReport;
    private final GetCustomerSegments getCustomerSegments;
    private final GetProductPerformance getProductPerformance;
    private final GetSalesForecast getSalesForecast;

    @GetMapping("/report")
    public SalesReport report(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return getSalesReport.execute(storeId, authentication.getName(), from, to, granularity, branchId);
    }

    @GetMapping("/customers")
    public CustomerReport customers(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String segment,
            Authentication authentication
    ) {
        return getCustomerSegments.execute(storeId, authentication.getName(), segment);
    }

    @GetMapping("/products")
    public ProductReport products(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return getProductPerformance.execute(storeId, authentication.getName(), branchId);
    }

    @GetMapping("/forecast")
    public SalesForecast forecast(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return getSalesForecast.execute(storeId, authentication.getName(), branchId);
    }
}
