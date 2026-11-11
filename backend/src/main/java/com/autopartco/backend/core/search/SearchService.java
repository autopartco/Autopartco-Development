package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;

/**
 * Motor de búsqueda del marketplace sobre {@code search_listings} ({@link SearchSql}). La consulta SQL filtra, ordena,
 * deja un listado por producto y pagina; después solo la página se hidrata con {@link ListingQueries} (precio del
 * comprador y {@link ProductResponse}), de modo que las reglas de publicación y precio no se duplican. Si la consulta
 * de texto no da resultados, se repite con trigramas y se marca {@code fuzzy}.
 */
@Service
public class SearchService {
    /** Tope del catálogo compatible ({@code /marketplace/products} sin paginar). */
    public static final int MAX_CATALOG_SIZE = 10_000;
    static final long READY_RECHECK_NANOS = 30_000_000_000L;
    static final String HAS_ENTRIES_SQL = "select exists (select 1 from search_listings)";

    private final JdbcClient jdbcClient;
    private final QueryParser parser;
    private final ListingQueries listingQueries;
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicLong lastReadyCheck = new AtomicLong(Long.MIN_VALUE);

    public SearchService(JdbcClient jdbcClient, QueryParser parser, ListingQueries listingQueries) {
        this.jdbcClient = jdbcClient;
        this.parser = parser;
        this.listingQueries = listingQueries;
    }

    /** {@code GET /marketplace/search}: valida los límites públicos ({@link SearchQuery#MAX_SIZE}) y busca. */
    @Transactional(readOnly = true)
    public SearchResult search(SearchQuery query, BuyerContext buyer) {
        query.validate(SearchQuery.MAX_SIZE, SearchQuery.MAX_PAGE);
        return run(query, buyer);
    }

    /** Catálogo compatible ({@code /marketplace/products}): mismas reglas con páginas de hasta {@link #MAX_CATALOG_SIZE}. */
    @Transactional(readOnly = true)
    public SearchResult catalog(SearchQuery query, BuyerContext buyer) {
        query.validate(MAX_CATALOG_SIZE, Integer.MAX_VALUE / MAX_CATALOG_SIZE);
        return run(query, buyer);
    }

    /**
     * {@code true} cuando {@code search_listings} ya tiene filas (tras la primera reconstrucción). Mientras esté vacía,
     * el catálogo compatible sigue con la consulta anterior. Se comprueba como mucho cada 30 s; una vez lista, no se
     * vuelve a consultar.
     */
    public boolean indexReady() {
        if (ready.get()) {
            return true;
        }
        long now = System.nanoTime();
        long last = lastReadyCheck.get();
        if (last != Long.MIN_VALUE && now - last < READY_RECHECK_NANOS) {
            return false;
        }
        lastReadyCheck.set(now);
        try {
            if (Boolean.TRUE.equals(jdbcClient.sql(HAS_ENTRIES_SQL).query(Boolean.class).single())) {
                ready.set(true);
            }
        } catch (RuntimeException ignored) {
            // Sin índice disponible se sigue con la consulta anterior.
        }
        return ready.get();
    }

    /** Lo marca listo sin consultar (lo llama el indexador al terminar una reconstrucción). */
    void markReady() {
        ready.set(true);
    }

    private SearchResult run(SearchQuery query, BuyerContext buyer) {
        BuyerContext effectiveBuyer = buyer == null ? BuyerContext.anonymous() : buyer;
        ParsedQuery parsed = parser.parse(query.q(), query.interpretOrDefault());
        SearchCriteria criteria = SearchCriteria.of(query, parsed);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        Page found = fetch(criteria, page, size);
        boolean fuzzy = false;
        if (found.total() == 0 && parsed.hasText() && parsed.fuzzyText() != null) {
            SearchCriteria similar = criteria.fuzzy(parsed.fuzzyText());
            jdbcClient.sql(SearchSql.FUZZY_THRESHOLD_SQL).query(String.class).single();
            Page similarPage = fetch(similar, page, size);
            if (similarPage.total() > 0) {
                criteria = similar;
                found = similarPage;
                fuzzy = true;
            }
        }
        List<SearchResult.Hit> items = hydrate(found.rows(), effectiveBuyer);
        SearchResult.Facets facets = query.facetsOrDefault() ? facets(criteria) : null;
        SearchInterpretation interpreted = query.interpretOrDefault() ? parsed.interpretation() : null;
        return new SearchResult(items, page, size, found.total(), fuzzy, interpreted, facets);
    }

    /** Fila de la página: listado elegido, distancia (si hay ubicación) y total de productos de la búsqueda. */
    record Row(UUID productId, UUID branchId, Double distanceKm, long total) {
    }

    record Page(List<Row> rows, long total) {
    }

    Page fetch(SearchCriteria criteria, int page, int size) {
        SearchSql.Statement statement = SearchSql.page(criteria, size, (long) page * size);
        List<Row> rows = jdbcClient.sql(statement.sql())
                .params(statement.params())
                .query((rs, rowNum) -> {
                    double distance = rs.getDouble("distance_km");
                    Double km = rs.wasNull() ? null : distance;
                    return new Row(rs.getObject("product_id", UUID.class), rs.getObject("branch_id", UUID.class), km,
                            rs.getLong("total"));
                })
                .list();
        if (rows.isEmpty() && page > 0) {
            SearchSql.Statement count = SearchSql.count(criteria);
            Long counted = jdbcClient.sql(count.sql()).params(count.params()).query(Long.class).single();
            return new Page(List.of(), counted == null ? 0 : counted);
        }
        return new Page(rows, rows.isEmpty() ? 0 : rows.getFirst().total());
    }

    private List<SearchResult.Hit> hydrate(List<Row> rows, BuyerContext buyer) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<ListingKey, Double> distances = new HashMap<>();
        List<ListingKey> keys = new ArrayList<>();
        for (Row row : rows) {
            ListingKey key = new ListingKey(row.productId(), row.branchId());
            keys.add(key);
            distances.put(key, row.distanceKm());
        }
        return listingQueries.publishedListingsByKeys(keys, buyer).stream()
                .map(listing -> new SearchResult.Hit(listing,
                        roundKm(distances.get(new ListingKey(listing.id(), listing.branchId())))))
                .toList();
    }

    static BigDecimal roundKm(Double km) {
        return km == null ? null : BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
    }

    SearchResult.Facets facets(SearchCriteria criteria) {
        SearchSql.Statement statement = SearchSql.facets(criteria);
        List<SearchResult.IdCount> categories = new ArrayList<>();
        List<SearchResult.IdCount> manufacturers = new ArrayList<>();
        List<SearchResult.CityCount> cities = new ArrayList<>();
        BigDecimal[] price = new BigDecimal[2];
        long[] flags = new long[2];
        jdbcClient.sql(statement.sql()).params(statement.params()).query((rs, rowNum) -> {
            boolean byCategory = rs.getInt("g_category") == 0;
            boolean byManufacturer = rs.getInt("g_manufacturer") == 0;
            boolean byCity = rs.getInt("g_city") == 0;
            if (byCategory) {
                UUID id = rs.getObject("category_id", UUID.class);
                long count = rs.getLong("n_category");
                if (id != null && count > 0) {
                    categories.add(new SearchResult.IdCount(id, rs.getString("category_name"), count));
                }
            } else if (byManufacturer) {
                UUID id = rs.getObject("manufacturer_id", UUID.class);
                long count = rs.getLong("n_manufacturer");
                if (id != null && count > 0) {
                    manufacturers.add(new SearchResult.IdCount(id, rs.getString("manufacturer_name"), count));
                }
            } else if (byCity) {
                String code = rs.getString("city_code");
                long count = rs.getLong("n_city");
                if (code != null && count > 0) {
                    cities.add(new SearchResult.CityCount(code.strip(), rs.getString("city_name"), count));
                }
            } else {
                price[0] = rs.getBigDecimal("price_min");
                price[1] = rs.getBigDecimal("price_max");
                flags[0] = rs.getLong("n_in_stock");
                flags[1] = rs.getLong("n_on_sale");
            }
            return rowNum;
        }).list();
        return new SearchResult.Facets(top(categories, SearchResult.IdCount::count),
                top(manufacturers, SearchResult.IdCount::count), top(cities, SearchResult.CityCount::count),
                new SearchResult.PriceRange(price[0], price[1]), flags[0], flags[1]);
    }

    private static <T> List<T> top(List<T> values, java.util.function.ToLongFunction<T> count) {
        return values.stream()
                .sorted(Comparator.comparingLong(count).reversed())
                .limit(SearchSql.MAX_FACET_VALUES)
                .toList();
    }
}
