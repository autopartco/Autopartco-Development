package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;

import lombok.extern.slf4j.Slf4j;

/**
 * Autocompletado ({@code GET /marketplace/search/suggest}): hasta {@value #MAX_SUGGESTIONS} términos de
 * {@code search_suggestions} (prefijo o trigramas) y hasta {@value #MAX_PRODUCTS} productos rápidos con precio general
 * (la respuesta se cachea como pública, así que nunca lleva precios personalizados).
 *
 * <p>{@link #reload()} reconstruye la tabla con categorías, fabricantes, marcas y modelos que tienen productos
 * publicados (peso = productos) y las búsquedas de los últimos 30 días con resultados hechas en
 * {@value #MIN_QUERY_SESSIONS} sesiones o más y en al menos {@value #MIN_QUERY_DAYS} días distintos (peso = sesiones).</p>
 */
@Slf4j
@Service
public class Suggestions {
    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_SUGGESTIONS = 8;
    public static final int MAX_PRODUCTS = 4;
    static final int MAX_TERM_LENGTH = 120;
    /**
     * Umbral de las búsquedas que pasan a sugerencia. {@code session_hash} sale de una cabecera que controla el
     * cliente y {@code marketplace_searches} no guarda otra fuente (ni {@code ap_aid} ni IP con hash): rotarla basta
     * para sumar sesiones, así que se exigen más sesiones y días distintos (zona America/Bogota).
     */
    static final int MIN_QUERY_SESSIONS = 5;
    static final int MIN_QUERY_DAYS = 2;

    static final String SUGGEST_SQL = """
            select term, kind
            from search_suggestions
            where term like :prefix or term % :q
            order by (term like :prefix) desc, similarity(term, :q) desc, weight desc, term
            limit :limit
            """;

    static final String CLEAR_SQL = "delete from search_suggestions";

    static final String RELOAD_SQL = """
            insert into search_suggestions (term, kind, weight, updated_at)
            select distinct on (term) term, kind, weight, now()
            from (
                select public.immutable_unaccent(lower(replace(c.code, '_', ' '))) as term, 'CATEGORY' as kind,
                       count(distinct sl.product_id)::int as weight
                from categories c
                join search_listings sl on sl.category_id = c.id
                where c.deleted_at is null
                group by c.code
                union all
                select public.immutable_unaccent(lower(m.name)), 'MANUFACTURER', count(distinct sl.product_id)::int
                from manufacturers m
                join search_listings sl on sl.manufacturer_id = m.id
                where m.deleted_at is null and m.approved
                group by m.name
                union all
                select public.immutable_unaccent(lower(mk.name)), 'VEHICLE', count(distinct sl.product_id)::int
                from make mk
                join search_listings sl on sl.vehicle_make_ids @> array[mk.id]
                where mk.deleted_at is null
                group by mk.name
                union all
                select public.immutable_unaccent(lower(mk.name || ' ' || md.name)), 'VEHICLE',
                       count(distinct sl.product_id)::int
                from model md
                join make mk on mk.id = md.make_id and mk.deleted_at is null
                join search_listings sl on sl.vehicle_model_ids @> array[md.id]
                where md.deleted_at is null
                group by mk.name, md.name
                union all
                select ms.query_normalized, 'QUERY', count(distinct ms.session_hash)::int
                from marketplace_searches ms
                where ms.occurred_at >= now() - interval '30 days'
                  and ms.results_count > 0
                  and ms.query_normalized is not null
                  and length(ms.query_normalized) between 2 and %d
                group by ms.query_normalized
                having count(distinct ms.session_hash) >= %d
                   and count(distinct (ms.occurred_at at time zone 'America/Bogota')::date) >= %d
            ) s
            where term is not null and term <> ''
            order by term, weight desc
            on conflict (term) do update set kind = excluded.kind, weight = excluded.weight, updated_at = now()
            """.formatted(MAX_TERM_LENGTH, MIN_QUERY_SESSIONS, MIN_QUERY_DAYS);

    public record Suggestion(String term, String kind) {
    }

    public record Result(List<Suggestion> suggestions, List<ProductResponse> products) {
        public static final Result EMPTY = new Result(List.of(), List.of());
    }

    private final JdbcClient jdbcClient;
    private final SearchService searchService;
    private final TypedCache<Result> cache;

    @Autowired
    public Suggestions(JdbcClient jdbcClient, SearchService searchService, ResilientCacheManager caches) {
        this.jdbcClient = jdbcClient;
        this.searchService = searchService;
        this.cache = caches == null ? null : caches.typed(AppCaches.SEARCH_SUGGEST, Result.class);
    }

    /** Sin caché (pruebas). */
    public Suggestions(JdbcClient jdbcClient, SearchService searchService) {
        this(jdbcClient, searchService, null);
    }

    /**
     * Menos de {@value #MIN_QUERY_LENGTH} caracteres útiles: respuesta vacía. Se cachea 5 min ({@code search:suggest})
     * por texto normalizado y punto; {@link #reload()} vacía la caché.
     */
    public Result suggest(String q, BigDecimal lat, BigDecimal lng) {
        String normalized = QueryParser.normalizeName(q);
        if (normalized == null || normalized.length() < MIN_QUERY_LENGTH) {
            return Result.EMPTY;
        }
        if (normalized.length() > QueryParser.MAX_LENGTH) {
            normalized = normalized.substring(0, QueryParser.MAX_LENGTH).strip();
        }
        if (cache == null) {
            return load(q, normalized, lat, lng);
        }
        String term = normalized;
        String key = term + '|' + (lat == null ? "" : lat.stripTrailingZeros().toPlainString()) + '|'
                + (lng == null ? "" : lng.stripTrailingZeros().toPlainString());
        return cache.get(key, () -> load(q, term, lat, lng));
    }

    private Result load(String q, String normalized, BigDecimal lat, BigDecimal lng) {
        // normalizeName deja solo [a-z0-9 ]: no hay comodines de LIKE que escapar.
        List<Suggestion> suggestions = jdbcClient.sql(SUGGEST_SQL)
                .params(Map.of("prefix", normalized + "%", "q", normalized, "limit", MAX_SUGGESTIONS))
                .query((rs, row) -> new Suggestion(rs.getString("term"), rs.getString("kind")))
                .list();
        SearchQuery query = SearchQuery.builder()
                .q(q)
                .lat(lat)
                .lng(lng)
                .page(0)
                .size(MAX_PRODUCTS)
                .facets(false)
                .build();
        List<ProductResponse> products = searchService.search(query, BuyerContext.anonymous()).items().stream()
                .map(SearchResult.Hit::listing)
                .toList();
        return new Result(suggestions, products);
    }

    /** Reconstruye {@code search_suggestions} en una transacción (la tabla nunca queda vacía para los lectores). */
    @Transactional
    public int reload() {
        jdbcClient.sql(CLEAR_SQL).update();
        int loaded = jdbcClient.sql(RELOAD_SQL).update();
        log.info("Sugerencias de búsqueda recargadas: {} términos.", loaded);
        if (cache != null) {
            cache.clear();
        }
        return loaded;
    }
}
