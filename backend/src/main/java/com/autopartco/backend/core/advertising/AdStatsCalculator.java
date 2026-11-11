package com.autopartco.backend.core.advertising;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Cálculo puro de {@link AdStats} a partir de filas agregadas de {@code ad_events}. */
final class AdStatsCalculator {
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_RATIO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private AdStatsCalculator() {
    }

    /**
     * Conteo de un tipo de evento en un día, ubicación y producto.
     *
     * @param events eventos (filas)
     * @param orders pedidos distintos (solo {@code ORDER})
     * @param amount suma de {@code amount} (solo {@code ORDER})
     */
    record Row(LocalDate day, AdPlacement placement, UUID productId, String productName, AdEventKind kind,
            long events, long orders, BigDecimal amount) {
    }

    /** Visitantes distintos con impresión y pedidos distintos de un día. */
    record DayTotals(LocalDate day, long reach, long orders) {
    }

    private static final class Counter {
        long impressions;
        long clicks;
        long whatsappClicks;
        long addToCart;
        long orders;
        BigDecimal revenue = BigDecimal.ZERO;

        void add(Row row) {
            switch (row.kind()) {
                case IMPRESSION -> impressions += row.events();
                case CLICK -> clicks += row.events();
                case WHATSAPP_CLICK -> whatsappClicks += row.events();
                case ADD_TO_CART -> addToCart += row.events();
                case ORDER -> {
                    orders += row.orders();
                    revenue = revenue.add(row.amount() == null ? BigDecimal.ZERO : row.amount());
                }
                default -> {
                }
            }
        }
    }

    static AdStats compute(LocalDate from, LocalDate to, List<Row> rows, List<DayTotals> dayTotals, BigDecimal cost) {
        Counter total = new Counter();
        Map<LocalDate, Counter> byDay = new TreeMap<>();
        Map<AdPlacement, Counter> byPlacement = new EnumMap<>(AdPlacement.class);
        Map<UUID, Counter> byProduct = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Row row : rows) {
            total.add(row);
            byDay.computeIfAbsent(row.day(), ignored -> new Counter()).add(row);
            byPlacement.computeIfAbsent(row.placement(), ignored -> new Counter()).add(row);
            byProduct.computeIfAbsent(row.productId(), ignored -> new Counter()).add(row);
            if (row.productName() != null) {
                names.putIfAbsent(row.productId(), row.productName());
            }
        }
        Map<LocalDate, DayTotals> totalsByDay = new TreeMap<>();
        for (DayTotals day : dayTotals) {
            totalsByDay.put(day.day(), day);
        }
        long reach = dayTotals.stream().mapToLong(DayTotals::reach).sum();
        // Un pedido con varios productos anunciados cuenta una vez por día (no una por línea).
        long orders = dayTotals.isEmpty() ? total.orders : dayTotals.stream().mapToLong(DayTotals::orders).sum();

        List<AdStats.Daily> daily = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            Counter counter = byDay.getOrDefault(day, new Counter());
            DayTotals totals = totalsByDay.get(day);
            daily.add(new AdStats.Daily(day, counter.impressions, totals == null ? 0 : totals.reach(),
                    counter.clicks, counter.whatsappClicks, counter.addToCart,
                    totals == null ? counter.orders : totals.orders(), money(counter.revenue)));
        }
        List<AdStats.ByPlacement> placements = byPlacement.entrySet().stream()
                .map(entry -> new AdStats.ByPlacement(entry.getKey(), entry.getValue().impressions,
                        entry.getValue().clicks, ratio(entry.getValue().clicks, entry.getValue().impressions),
                        entry.getValue().whatsappClicks, entry.getValue().addToCart, entry.getValue().orders,
                        money(entry.getValue().revenue)))
                .toList();
        List<AdStats.ByProduct> products = byProduct.entrySet().stream()
                .map(entry -> new AdStats.ByProduct(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue().impressions, entry.getValue().clicks,
                        ratio(entry.getValue().clicks, entry.getValue().impressions),
                        entry.getValue().whatsappClicks, entry.getValue().addToCart, entry.getValue().orders,
                        money(entry.getValue().revenue)))
                .sorted(Comparator.comparingLong(AdStats.ByProduct::impressions).reversed())
                .toList();
        BigDecimal revenue = money(total.revenue);
        BigDecimal spent = cost == null ? ZERO_MONEY : money(cost);
        return new AdStats(from, to, total.impressions, reach, total.clicks, ratio(total.clicks, total.impressions),
                total.whatsappClicks, total.addToCart, orders, revenue, spent,
                total.clicks == 0 ? ZERO_MONEY : spent.divide(BigDecimal.valueOf(total.clicks), 2,
                        RoundingMode.HALF_UP),
                spent.signum() == 0 ? ZERO_RATIO : revenue.divide(spent, 4, RoundingMode.HALF_UP),
                daily, placements, products);
    }

    /**
     * Costo del rango: precio pagado ÷ días de la campaña × días de la campaña dentro de [{@code from}, {@code to}].
     * 0 si no está pagada.
     */
    static BigDecimal proratedCost(BigDecimal totalPrice, boolean paid, LocalDate startsOn, LocalDate endsOn,
            LocalDate from, LocalDate to) {
        if (!paid || totalPrice == null) {
            return ZERO_MONEY;
        }
        LocalDate start = startsOn.isAfter(from) ? startsOn : from;
        LocalDate end = endsOn.isBefore(to) ? endsOn : to;
        if (end.isBefore(start)) {
            return ZERO_MONEY;
        }
        long overlap = AdCampaignRules.days(start, end);
        long days = AdCampaignRules.days(startsOn, endsOn);
        return totalPrice.multiply(BigDecimal.valueOf(overlap)).divide(BigDecimal.valueOf(days), 2,
                RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return ZERO_RATIO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
