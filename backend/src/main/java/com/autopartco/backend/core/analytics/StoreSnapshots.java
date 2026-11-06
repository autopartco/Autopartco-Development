package com.autopartco.backend.core.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Arma la «foto» de un almacén para {@link AlertRules} reutilizando las lecturas de la analítica: ventas diarias
 * del último año, rendimiento de productos ({@link ProductPerformance}: clase ABC, cobertura y stock) y la
 * segmentación de clientes de hoy y de hace {@value AlertRules#CUSTOMER_COMPARISON_DAYS} días (misma clasificación
 * de {@link CustomerSegment}, sin tablas nuevas). Solo productos publicados ({@code ACTIVE}). Sin permisos.
 */
@Service
@AllArgsConstructor
public class StoreSnapshots {
    private static final int HISTORY_DAYS = 365;

    private final SalesAnalyticsQueries salesQueries;
    private final CustomerAnalyticsQueries customerQueries;
    private final ProductPerformance productPerformance;

    @Transactional(readOnly = true)
    public AlertRules.Snapshot capture(UUID storeId) {
        return capture(storeId, Analytics.today());
    }

    AlertRules.Snapshot capture(UUID storeId, LocalDate today) {
        DateRange history = new DateRange(today.minusDays(HISTORY_DAYS - 1L), today);
        List<AlertRules.DaySales> daily = salesQueries.daily(storeId, null, history).stream()
                .map(day -> new AlertRules.DaySales(day.day(), day.revenue()))
                .toList();
        return new AlertRules.Snapshot(storeId, today, daily, products(storeId, today, history),
                customers(storeId, today));
    }

    private List<AlertRules.ProductState> products(UUID storeId, LocalDate today, DateRange history) {
        Set<UUID> published = salesQueries.activeProductIds(storeId);
        Map<UUID, Long> units30 = new HashMap<>();
        Map<UUID, LocalDate> lastSale = new HashMap<>();
        LocalDate recentStart = today.minusDays(AlertRules.STOCKOUT_LOOKBACK_DAYS - 1L);
        for (SalesAnalyticsQueries.ProductDailySales sale : salesQueries.productDaily(storeId, null, history)) {
            if (sale.units() <= 0 || sale.productId() == null) {
                continue;
            }
            lastSale.merge(sale.productId(), sale.day(), (a, b) -> a.isAfter(b) ? a : b);
            if (!sale.day().isBefore(recentStart)) {
                units30.merge(sale.productId(), sale.units(), Long::sum);
            }
        }
        Map<UUID, Long> age = new HashMap<>();
        for (SalesAnalyticsQueries.ProductStock stock : salesQueries.productStock(storeId, null)) {
            age.put(stock.productId(), stock.createdAt() == null ? 0L : ChronoUnit.DAYS.between(
                    stock.createdAt().atZoneSameInstant(Analytics.ZONE).toLocalDate(), today));
        }
        List<AlertRules.ProductState> states = new ArrayList<>();
        for (ProductReport.Product product : productPerformance.report(storeId, null, today).products()) {
            if (!published.contains(product.productId())) {
                continue;
            }
            LocalDate last = lastSale.get(product.productId());
            states.add(new AlertRules.ProductState(
                    product.productId(),
                    product.name(),
                    product.abcClass(),
                    product.coverageDays() == null ? null : product.coverageDays().doubleValue(),
                    product.stock(),
                    units30.getOrDefault(product.productId(), 0L),
                    last == null ? null : (int) ChronoUnit.DAYS.between(last, today),
                    age.getOrDefault(product.productId(), 0L)));
        }
        return states;
    }

    private List<AlertRules.CustomerState> customers(UUID storeId, LocalDate today) {
        LocalDate before = today.minusDays(AlertRules.CUSTOMER_COMPARISON_DAYS);
        List<CustomerSegments.Classified> current = CustomerSegments.classify(
                customerQueries.activity(storeId, windowStart(today)), today);
        Map<UUID, CustomerSegment> previous = new HashMap<>();
        for (CustomerSegments.Classified customer : CustomerSegments.classify(
                customerQueries.activityUntil(storeId, windowStart(before), Analytics.startOf(before.plusDays(1))),
                before)) {
            previous.put(customer.activity().personId(), customer.segment());
        }
        List<AlertRules.CustomerState> states = new ArrayList<>(current.size());
        for (CustomerSegments.Classified customer : current) {
            UUID personId = customer.activity().personId();
            states.add(new AlertRules.CustomerState(personId, customer.activity().name(), customer.segment(),
                    previous.get(personId)));
        }
        return states;
    }

    private static java.time.OffsetDateTime windowStart(LocalDate day) {
        return Analytics.startOf(day.minusDays(CustomerSegment.WINDOW_DAYS - 1L));
    }
}
