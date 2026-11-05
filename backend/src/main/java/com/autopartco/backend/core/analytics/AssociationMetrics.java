package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cálculo puro de asociaciones entre productos (soporte, confianza y lift) sobre canastas: cada canasta es el
 * conjunto de productos distintos de un pedido dentro del ámbito.
 */
final class AssociationMetrics {
    /** Pedidos con al menos 2 productos distintos que exige un ámbito para calcular pares. */
    static final int MIN_MULTI_PRODUCT_BASKETS = 20;
    /** Pedidos en común mínimos para conservar un par. */
    static final int MIN_COMMON_ORDERS = 2;
    /** Consecuentes que se guardan por antecedente. */
    static final int TOP_PER_ANTECEDENT = 20;
    /** Canastas más grandes no generan pares (evita explosión cuadrática); sí cuentan en los totales. */
    static final int MAX_BASKET_SIZE = 50;
    /** Confianza mínima para sugerir un combo. */
    static final BigDecimal COMBO_MIN_CONFIDENCE = new BigDecimal("0.2");

    private AssociationMetrics() {
    }

    /** Par dirigido antecedente → consecuente; {@code orders} son los pedidos con ambos. */
    record Pair(UUID antecedent, UUID consequent, BigDecimal support, BigDecimal confidence, BigDecimal lift,
            int orders) {
    }

    /**
     * Pares del ámbito, en ambos sentidos. Vacío si el ámbito tiene menos de {@value #MIN_MULTI_PRODUCT_BASKETS}
     * pedidos con 2 o más productos distintos (sin volumen: se usa el respaldo).
     *
     * <ul>
     *   <li>support = pedidos con A y B / pedidos totales</li>
     *   <li>confidence = pedidos con A y B / pedidos con A</li>
     *   <li>lift = confidence / (pedidos con B / pedidos totales)</li>
     * </ul>
     */
    static Optional<List<Pair>> pairs(Collection<? extends Set<UUID>> baskets) {
        long multi = baskets.stream().filter(basket -> basket.size() >= 2).count();
        if (multi < MIN_MULTI_PRODUCT_BASKETS) {
            return Optional.empty();
        }
        long total = baskets.size();
        Map<UUID, Integer> itemOrders = new HashMap<>();
        Map<UUID, Map<UUID, Integer>> common = new HashMap<>();
        for (Set<UUID> basket : baskets) {
            basket.forEach(product -> itemOrders.merge(product, 1, Integer::sum));
            if (basket.size() < 2 || basket.size() > MAX_BASKET_SIZE) {
                continue;
            }
            List<UUID> items = new ArrayList<>(basket);
            for (UUID first : items) {
                for (UUID second : items) {
                    if (!first.equals(second)) {
                        common.computeIfAbsent(first, key -> new HashMap<>()).merge(second, 1, Integer::sum);
                    }
                }
            }
        }
        List<Pair> result = new ArrayList<>();
        common.forEach((antecedent, consequents) -> {
            List<Pair> candidates = new ArrayList<>();
            consequents.forEach((consequent, together) -> {
                if (together >= MIN_COMMON_ORDERS) {
                    candidates.add(pair(antecedent, consequent, together, itemOrders, total));
                }
            });
            candidates.sort(Comparator.comparing(Pair::lift).reversed()
                    .thenComparing(Comparator.comparing(Pair::confidence).reversed())
                    .thenComparing(Comparator.comparingInt(Pair::orders).reversed())
                    .thenComparing(Pair::consequent));
            result.addAll(candidates.subList(0, Math.min(TOP_PER_ANTECEDENT, candidates.size())));
        });
        return Optional.of(result);
    }

    private static Pair pair(UUID antecedent, UUID consequent, int together, Map<UUID, Integer> itemOrders,
            long total) {
        double support = (double) together / total;
        double confidence = (double) together / itemOrders.get(antecedent);
        double lift = confidence / ((double) itemOrders.get(consequent) / total);
        return new Pair(antecedent, consequent, decimal(support), decimal(confidence), decimal(lift), together);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Par con mayor lift entre los de confianza de al menos {@value #COMBO_MIN_CONFIDENCE}; vacío si ninguno cumple.
     * Entre pares simétricos (A→B y B→A) con el mismo lift gana el de menor id del antecedente, para que sea estable.
     */
    static Optional<Pair> suggestedCombo(Collection<Pair> pairs) {
        return pairs.stream()
                .filter(pair -> pair.confidence().compareTo(COMBO_MIN_CONFIDENCE) >= 0)
                .max(Comparator.comparing(Pair::lift)
                        .thenComparing(Pair::confidence)
                        .thenComparing(Comparator.comparing(Pair::antecedent).reversed())
                        .thenComparing(Comparator.comparing(Pair::consequent).reversed()));
    }
}
