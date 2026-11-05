package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AssociationMetricsTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    /** 25 pedidos: 12 con A+B, 4 con A+C, 4 con B+C y 5 solo con D (20 con 2 productos). */
    private static List<Set<UUID>> basketOf25() {
        List<Set<UUID>> baskets = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            baskets.add(Set.of(A, B));
        }
        for (int i = 0; i < 4; i++) {
            baskets.add(Set.of(A, C));
            baskets.add(Set.of(B, C));
        }
        for (int i = 0; i < 5; i++) {
            baskets.add(Set.of(D));
        }
        return baskets;
    }

    private static AssociationMetrics.Pair find(List<AssociationMetrics.Pair> pairs, UUID from, UUID to) {
        return pairs.stream().filter(pair -> pair.antecedent().equals(from) && pair.consequent().equals(to))
                .findFirst().orElseThrow();
    }

    @Test
    void computesSupportConfidenceAndLiftInBothDirections() {
        List<AssociationMetrics.Pair> pairs = AssociationMetrics.pairs(basketOf25()).orElseThrow();

        // A en 16 pedidos, B en 16, C en 8, total 25.
        AssociationMetrics.Pair ab = find(pairs, A, B);
        assertThat(ab.orders()).isEqualTo(12);
        assertThat(ab.support()).isEqualByComparingTo("0.48");
        assertThat(ab.confidence()).isEqualByComparingTo("0.75");
        assertThat(ab.lift()).isEqualByComparingTo("1.171875");
        assertThat(find(pairs, B, A).confidence()).isEqualByComparingTo("0.75");

        AssociationMetrics.Pair ac = find(pairs, A, C);
        assertThat(ac.support()).isEqualByComparingTo("0.16");
        assertThat(ac.confidence()).isEqualByComparingTo("0.25");
        assertThat(ac.lift()).isEqualByComparingTo("0.78125");

        AssociationMetrics.Pair ca = find(pairs, C, A);
        assertThat(ca.confidence()).isEqualByComparingTo("0.5");
        assertThat(ca.lift()).isEqualByComparingTo("0.78125");
        assertThat(pairs).hasSize(6);
    }

    @Test
    void needsTwentyOrdersWithTwoDistinctProducts() {
        List<Set<UUID>> nineteen = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            nineteen.add(Set.of(A, B));
        }
        for (int i = 0; i < 30; i++) {
            nineteen.add(Set.of(D)); // los pedidos de un solo producto no cuentan para el umbral
        }
        assertThat(AssociationMetrics.pairs(nineteen)).isEqualTo(Optional.empty());

        nineteen.add(Set.of(A, B));
        assertThat(AssociationMetrics.pairs(nineteen)).isPresent();
    }

    @Test
    void dropsPairsWithFewerThanTwoCommonOrders() {
        List<Set<UUID>> baskets = basketOf25();
        baskets.add(Set.of(A, D)); // una sola vez juntos

        List<AssociationMetrics.Pair> pairs = AssociationMetrics.pairs(baskets).orElseThrow();

        assertThat(pairs).noneMatch(pair -> pair.consequent().equals(D) || pair.antecedent().equals(D));
    }

    @Test
    void keepsOnlyTheTwentyBestConsequentsPerAntecedent() {
        List<Set<UUID>> baskets = new ArrayList<>();
        UUID anchor = new UUID(1, 0);
        for (int product = 1; product <= 25; product++) {
            UUID other = new UUID(2, product);
            baskets.add(Set.of(anchor, other));
            baskets.add(Set.of(anchor, other));
        }

        List<AssociationMetrics.Pair> pairs = AssociationMetrics.pairs(baskets).orElseThrow();

        assertThat(pairs.stream().filter(pair -> pair.antecedent().equals(anchor))).hasSize(20);
    }

    @Test
    void suggestedComboIsTheHighestLiftWithConfidenceOfAtLeastTwentyPercent() {
        List<AssociationMetrics.Pair> pairs = AssociationMetrics.pairs(basketOf25()).orElseThrow();

        AssociationMetrics.Pair combo = AssociationMetrics.suggestedCombo(pairs).orElseThrow();

        assertThat(combo.antecedent()).isEqualTo(A);
        assertThat(combo.consequent()).isEqualTo(B);
    }

    @Test
    void suggestedComboIgnoresPairsBelowMinimumConfidenceAndIsEmptyIfNone() {
        AssociationMetrics.Pair weak = new AssociationMetrics.Pair(A, B,
                new java.math.BigDecimal("0.05"), new java.math.BigDecimal("0.19"),
                new java.math.BigDecimal("9"), 3);
        AssociationMetrics.Pair ok = new AssociationMetrics.Pair(A, C,
                new java.math.BigDecimal("0.10"), new java.math.BigDecimal("0.20"),
                new java.math.BigDecimal("1.5"), 5);

        assertThat(AssociationMetrics.suggestedCombo(List.of(weak, ok)).orElseThrow().consequent()).isEqualTo(C);
        assertThat(AssociationMetrics.suggestedCombo(List.of(weak))).isEmpty();
    }
}
