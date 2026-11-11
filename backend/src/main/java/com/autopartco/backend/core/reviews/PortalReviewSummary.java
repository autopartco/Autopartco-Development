package com.autopartco.backend.core.reviews;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de reseñas para el portal: el del almacén (solo reseñas visibles), cuántas reseñas visibles siguen sin
 * respuesta (de almacén y de productos) y los productos mejor y peor calificados (hasta 5 cada lista, con al menos una
 * reseña visible).
 *
 * @param average      promedio del almacén con un decimal; {@code null} sin reseñas
 * @param distribution reseñas por estrellas, claves {@code "1"} a {@code "5"}
 * @param highlights   categorías destacadas (insignias) del almacén
 */
public record PortalReviewSummary(
        BigDecimal average,
        long count,
        Map<String, Long> distribution,
        List<AspectStat> aspects,
        List<String> highlights,
        long unanswered,
        List<ProductRating> topProducts,
        List<ProductRating> bottomProducts
) {
    public record AspectStat(String aspect, BigDecimal average, long count) {
    }

    public record ProductRating(UUID productId, String name, BigDecimal average, long count) {
    }

    public PortalReviewSummary {
        distribution = distribution == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(distribution));
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        topProducts = topProducts == null ? List.of() : List.copyOf(topProducts);
        bottomProducts = bottomProducts == null ? List.of() : List.copyOf(bottomProducts);
    }
}
