package com.autopartco.backend.core.search;

import java.util.List;
import java.util.function.Supplier;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos agregados del inicio del marketplace, leídos del índice {@code search_listings} (solo productos publicados) y
 * de {@code search_suggestions}. Solo cifras y términos; nada personal.
 */
@Service
public class HomeFacts {
    public static final int MAX_POPULAR_SEARCHES = 8;
    public static final int MAX_CATEGORIES = 30;

    static final String STATS_SQL = """
            select count(distinct store_id) as stores, count(distinct city_code) as cities,
                   count(distinct product_id) as products
            from search_listings
            """;

    /** Términos de búsqueda reales de los últimos 30 días (ver {@link Suggestions#reload()}), por número de sesiones. */
    static final String POPULAR_SQL = """
            select term from search_suggestions
            where kind = 'QUERY'
            order by weight desc, term
            limit :limit
            """;

    static final String CATEGORIES_SQL = """
            select c.id, c.code, count(distinct sl.product_id) as products
            from search_listings sl
            join categories c on c.id = sl.category_id and c.deleted_at is null
            group by c.id, c.code
            order by products desc, c.code
            limit :limit
            """;

    public record Stats(long stores, long cities, long products) {
    }

    public record CategoryCount(UUID id, String code, long productCount) {
    }

    private static final long TTL_NANOS = 600_000_000_000L;

    /** Valor global con caducidad; no depende del área, así que se comparte entre todas las peticiones. */
    private static final class Memo<T> {
        private record Entry<T>(T value, long expiresAt) {
        }

        private volatile Entry<T> entry;

        synchronized T get(Supplier<T> loader) {
            Entry<T> current = entry;
            if (current != null && current.expiresAt() - System.nanoTime() > 0) {
                return current.value();
            }
            T value = loader.get();
            entry = new Entry<>(value, System.nanoTime() + TTL_NANOS);
            return value;
        }

        void clear() {
            entry = null;
        }
    }

    private final JdbcClient jdbcClient;
    private final Memo<Stats> statsMemo = new Memo<>();
    private final Memo<List<CategoryCount>> categoriesMemo = new Memo<>();

    public HomeFacts(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Almacenes, ciudades y repuestos publicados (caché global de 10 min; se vacía con {@link #invalidate()}). */
    public Stats stats() {
        return statsMemo.get(() -> jdbcClient.sql(STATS_SQL)
                .query((rs, row) -> new Stats(rs.getLong("stores"), rs.getLong("cities"), rs.getLong("products")))
                .single());
    }

    /** Descarta las cifras guardadas (el índice cambió). */
    public void invalidate() {
        statsMemo.clear();
        categoriesMemo.clear();
    }

    /** Hasta {@value #MAX_POPULAR_SEARCHES} términos; vacía si aún no hay búsquedas suficientes. */
    @Transactional(readOnly = true)
    public List<String> popularSearches() {
        return jdbcClient.sql(POPULAR_SQL).param("limit", MAX_POPULAR_SEARCHES)
                .query(String.class).list();
    }

    /** Categorías con productos publicados, de mayor a menor (el catálogo de categorías es plano: un solo nivel). */
    public List<CategoryCount> categories() {
        return categoriesMemo.get(() -> List.copyOf(jdbcClient.sql(CATEGORIES_SQL).param("limit", MAX_CATEGORIES)
                .query((rs, row) -> new CategoryCount(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getLong("products")))
                .list()));
    }
}
