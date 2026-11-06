package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Productos que se compran juntos. Cada noche a las 04:00 (America/Bogota) reescribe {@code product_associations} por
 * almacén y global, en una transacción por ámbito. Con volumen (al menos 20 pedidos con 2 o más productos distintos
 * en los últimos 365 días) guarda pares {@code ORDERS} con soporte, confianza y lift; un almacén sin volumen guarda
 * respaldos {@code COMPATIBILITY} (comparten reglas de vehículo) y {@code CATEGORY} (misma categoría) con métricas en
 * 0. {@code @EnableScheduling} lo activa {@code core.payments.internal.PaymentsConfig}.
 *
 * <p>Las lecturas ({@link #related}, {@link #storeReport}) no comprueban permisos: los casos de uso lo hacen.</p>
 */
@Slf4j
@Service
public class ProductAssociations {
    static final int LOOKBACK_DAYS = 365;
    /** Consecuentes de respaldo que se guardan por antecedente y por tipo. */
    static final int FALLBACK_PER_ANTECEDENT = 10;
    static final int REPORT_MAX_PAIRS = 50;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final ProductAssociationQueries queries;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public ProductAssociations(ProductAssociationQueries queries, PlatformTransactionManager transactionManager) {
        this(queries, new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    ProductAssociations(ProductAssociationQueries queries, TransactionOperations transactions, Clock clock) {
        this.queries = queries;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Producto recomendado junto a otro; {@code reason}: {@code ORDERS}, {@code COMPATIBILITY} o {@code CATEGORY}. */
    public record Related(UUID productId, String reason) {
    }

    public record ProductRef(UUID productId, String name) {
    }

    /** Par del reporte del almacén; {@code orders} son los pedidos con ambos productos (0 en los respaldos). */
    public record PairView(ProductRef antecedent, ProductRef consequent, BigDecimal support, BigDecimal confidence,
            BigDecimal lift, long orders) {
    }

    public record SuggestedCombo(List<UUID> productIds, List<String> names, String reason) {
    }

    /**
     * @param source origen de los pares: {@code ORDERS}, o el respaldo disponible; {@code null} sin datos
     * @param computedAt última recomputación de esos pares; {@code null} sin datos
     */
    public record StoreReport(OffsetDateTime computedAt, String source, List<PairView> pairs,
            SuggestedCombo suggestedCombo) {
    }

    // ---- Tarea nocturna ----------------------------------------------------------------------------------------

    @Scheduled(cron = "0 0 4 * * *", zone = "America/Bogota")
    public void recomputeNightly() {
        try {
            recompute();
        } catch (RuntimeException failure) {
            log.warn("Falló el cálculo nocturno de productos que se compran juntos: {}",
                    failure.getClass().getSimpleName());
        }
    }

    /** Reescribe todos los ámbitos; un fallo en un ámbito se registra y no detiene a los demás. */
    void recompute() {
        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(LOOKBACK_DAYS);
        Map<UUID, Set<UUID>> globalBaskets = new HashMap<>();
        Map<UUID, Map<UUID, Set<UUID>>> storeBaskets = new HashMap<>();
        for (ProductAssociationQueries.BasketLine line : queries.basketLines(since)) {
            globalBaskets.computeIfAbsent(line.orderId(), key -> new HashSet<>()).add(line.productId());
            storeBaskets.computeIfAbsent(line.storeId(), key -> new HashMap<>())
                    .computeIfAbsent(line.orderId(), key -> new HashSet<>()).add(line.productId());
        }
        Set<UUID> stores = new LinkedHashSet<>(queries.scopeStores());
        stores.addAll(storeBaskets.keySet());
        for (UUID store : stores) {
            runScope(store, () -> storeRows(store, storeBaskets.get(store), since));
        }
        runScope(null, () -> orderRows(globalBaskets).orElse(List.of()));
    }

    private void runScope(UUID storeId, java.util.function.Supplier<List<ProductAssociationQueries.Association>> rows) {
        try {
            List<ProductAssociationQueries.Association> toWrite = rows.get();
            transactions.executeWithoutResult(status -> {
                queries.deleteScope(storeId);
                queries.insert(storeId, toWrite);
            });
        } catch (RuntimeException failure) {
            log.warn("Falló el cálculo de asociaciones de un ámbito: {}", failure.getClass().getSimpleName());
        }
    }

    private List<ProductAssociationQueries.Association> storeRows(
            UUID storeId, Map<UUID, Set<UUID>> baskets, OffsetDateTime since) {
        Optional<List<ProductAssociationQueries.Association>> orders =
                baskets == null ? Optional.empty() : orderRows(baskets);
        if (orders.isPresent()) {
            return orders.get();
        }
        List<ProductAssociationQueries.Association> rows = new ArrayList<>();
        rows.addAll(fallbackRows(queries.compatible(storeId, null, FALLBACK_PER_ANTECEDENT, since),
                ProductAssociationQueries.COMPATIBILITY));
        rows.addAll(fallbackRows(queries.sameCategory(storeId, null, FALLBACK_PER_ANTECEDENT, since),
                ProductAssociationQueries.CATEGORY));
        return rows;
    }

    private static Optional<List<ProductAssociationQueries.Association>> orderRows(Map<UUID, Set<UUID>> baskets) {
        return AssociationMetrics.pairs(baskets.values()).map(pairs -> pairs.stream()
                .map(pair -> new ProductAssociationQueries.Association(pair.antecedent(), pair.consequent(),
                        pair.support(), pair.confidence(), pair.lift(), ProductAssociationQueries.ORDERS))
                .toList());
    }

    private static List<ProductAssociationQueries.Association> fallbackRows(
            List<ProductAssociationQueries.Link> links, String source) {
        return links.stream()
                .map(link -> new ProductAssociationQueries.Association(
                        link.antecedent(), link.consequent(), ZERO, ZERO, ZERO, source))
                .toList();
    }

    // ---- Lecturas ----------------------------------------------------------------------------------------------

    /**
     * Hasta {@code limit} productos publicados con stock que suelen comprarse con {@code productId}: primero las filas
     * del almacén del producto y luego las globales. Si la tabla no tiene filas utilizables para el producto, calcula
     * al vuelo el respaldo (compatibilidad y luego categoría). Producto inexistente: lista vacía.
     */
    public List<Related> related(UUID productId, int limit) {
        if (productId == null || limit <= 0) {
            return List.of();
        }
        UUID storeId = queries.productStore(productId);
        Map<UUID, Related> chosen = new LinkedHashMap<>();
        for (ProductAssociationQueries.RelatedRow row : queries.related(productId, storeId, limit * 3)) {
            chosen.putIfAbsent(row.consequent(), new Related(row.consequent(), row.source()));
        }
        if (chosen.isEmpty()) {
            OffsetDateTime since = OffsetDateTime.now(clock).minusDays(LOOKBACK_DAYS);
            queries.compatible(storeId, productId, limit, since).forEach(link -> chosen.putIfAbsent(
                    link.consequent(), new Related(link.consequent(), ProductAssociationQueries.COMPATIBILITY)));
            queries.sameCategory(storeId, productId, limit, since).forEach(link -> chosen.putIfAbsent(
                    link.consequent(), new Related(link.consequent(), ProductAssociationQueries.CATEGORY)));
        }
        return chosen.values().stream().limit(limit).toList();
    }

    /**
     * Pares del almacén: los {@code ORDERS} si existen; si no, los de compatibilidad y, en su defecto, los de
     * categoría. Combo sugerido: el par {@code ORDERS} de mayor lift con confianza de al menos 0.2.
     */
    public StoreReport storeReport(UUID storeId) {
        List<ProductAssociationQueries.StoredPair> stored = queries.storePairs(storeId);
        String source = null;
        for (String candidate : List.of(ProductAssociationQueries.ORDERS, ProductAssociationQueries.COMPATIBILITY,
                ProductAssociationQueries.CATEGORY)) {
            if (stored.stream().anyMatch(pair -> candidate.equals(pair.source()))) {
                source = candidate;
                break;
            }
        }
        if (source == null) {
            return new StoreReport(null, null, List.of(), null);
        }
        String chosenSource = source;
        List<ProductAssociationQueries.StoredPair> rows = stored.stream()
                .filter(pair -> chosenSource.equals(pair.source()))
                .sorted(Comparator.comparing(ProductAssociationQueries.StoredPair::lift).reversed()
                        .thenComparing(Comparator.comparing(ProductAssociationQueries.StoredPair::confidence)
                                .reversed()))
                .toList();
        OffsetDateTime computedAt = rows.stream().map(ProductAssociationQueries.StoredPair::computedAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        boolean fromOrders = ProductAssociationQueries.ORDERS.equals(chosenSource);
        long totalOrders = fromOrders
                ? queries.storeOrders(storeId, OffsetDateTime.now(clock).minusDays(LOOKBACK_DAYS))
                : 0;
        List<PairView> pairs = rows.stream()
                .limit(REPORT_MAX_PAIRS)
                .map(pair -> new PairView(
                        new ProductRef(pair.antecedentId(), pair.antecedentName()),
                        new ProductRef(pair.consequentId(), pair.consequentName()),
                        pair.support(), pair.confidence(), pair.lift(),
                        fromOrders ? ordersOf(pair.support(), totalOrders) : 0))
                .toList();
        SuggestedCombo combo = fromOrders ? suggestedCombo(rows, totalOrders) : null;
        return new StoreReport(computedAt, chosenSource, pairs, combo);
    }

    private static SuggestedCombo suggestedCombo(List<ProductAssociationQueries.StoredPair> rows, long totalOrders) {
        Map<ProductAssociationQueries.StoredPair, AssociationMetrics.Pair> byPair = new HashMap<>();
        for (ProductAssociationQueries.StoredPair row : rows) {
            byPair.put(row, new AssociationMetrics.Pair(row.antecedentId(), row.consequentId(), row.support(),
                    row.confidence(), row.lift(), (int) ordersOf(row.support(), totalOrders)));
        }
        return AssociationMetrics.suggestedCombo(byPair.values())
                .flatMap(best -> byPair.entrySet().stream()
                        .filter(entry -> entry.getValue() == best).findFirst().map(Map.Entry::getKey))
                .map(row -> new SuggestedCombo(
                        List.of(row.antecedentId(), row.consequentId()),
                        List.of(row.antecedentName(), row.consequentName()),
                        "Se compran juntos en " + ordersOf(row.support(), totalOrders) + " pedidos: "
                                + percent(row.confidence()) + "% de quienes compran «" + row.antecedentName()
                                + "» también llevan «" + row.consequentName() + "» (lift "
                                + row.lift().setScale(2, RoundingMode.HALF_UP) + ")."))
                .orElse(null);
    }

    private static long ordersOf(BigDecimal support, long totalOrders) {
        return support.multiply(BigDecimal.valueOf(totalOrders)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static String percent(BigDecimal fraction) {
        return fraction.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
