package com.autopartco.backend.marketplace.reviews;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resumen público de las reseñas visibles (sin borradas ni ocultas) de un producto o almacén.
 *
 * @param average      promedio general con un decimal; {@code null} sin reseñas
 * @param count        reseñas visibles
 * @param distribution reseñas por estrellas, claves {@code "1"}…{@code "5"} (siempre las cinco)
 * @param aspects      promedio (un decimal) y número de calificaciones por categoría, en el orden del enum; solo las
 *                     que tienen alguna
 * @param highlights   categorías destacadas (insignias): al menos {@value #BADGE_MIN_RATINGS} calificaciones y
 *                     promedio ≥ {@link #BADGE_MIN_AVERAGE}; como máximo {@value #MAX_HIGHLIGHTS}, de mayor a menor
 *                     promedio
 */
public record ReviewSummary(
        BigDecimal average,
        long count,
        Map<String, Long> distribution,
        List<AspectSummary> aspects,
        List<String> highlights
) {
    public static final int BADGE_MIN_RATINGS = 3;
    public static final BigDecimal BADGE_MIN_AVERAGE = new BigDecimal("4.3");
    public static final int MAX_HIGHLIGHTS = 3;

    public record AspectSummary(String aspect, BigDecimal average, long count) {
    }

    /** Promedio sin redondear de una categoría (lo que se compara con el umbral de la insignia). */
    record AspectStat(String aspect, BigDecimal average, long count) {
    }

    public ReviewSummary {
        distribution = distribution == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(distribution));
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }

    /**
     * @param byStars reseñas por estrellas (1–5); las que falten cuentan 0
     * @param stats   promedios crudos por categoría
     * @param order   orden de las categorías ({@link ReviewKind#aspects()})
     */
    static ReviewSummary of(Map<Integer, Long> byStars, List<AspectStat> stats, List<String> order) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        long count = 0;
        long sum = 0;
        for (int stars = 1; stars <= 5; stars++) {
            long n = byStars == null ? 0 : Math.max(0, byStars.getOrDefault(stars, 0L));
            distribution.put(String.valueOf(stars), n);
            count += n;
            sum += n * stars;
        }
        BigDecimal average = count == 0 ? null
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        List<AspectStat> valid = stats == null ? List.of() : stats.stream()
                .filter(stat -> stat.average() != null && stat.count() > 0 && order.contains(stat.aspect()))
                .sorted(Comparator.comparingInt(stat -> order.indexOf(stat.aspect())))
                .toList();
        List<AspectSummary> aspects = valid.stream()
                .map(stat -> new AspectSummary(stat.aspect(), stat.average().setScale(1, RoundingMode.HALF_UP),
                        stat.count()))
                .toList();
        List<String> highlights = valid.stream()
                .filter(stat -> stat.count() >= BADGE_MIN_RATINGS && stat.average().compareTo(BADGE_MIN_AVERAGE) >= 0)
                .sorted(Comparator.comparing(AspectStat::average).reversed()
                        .thenComparing(Comparator.comparingLong(AspectStat::count).reversed())
                        .thenComparingInt(stat -> order.indexOf(stat.aspect())))
                .limit(MAX_HIGHLIGHTS)
                .map(AspectStat::aspect)
                .toList();
        return new ReviewSummary(average, count, distribution, aspects, highlights);
    }
}
