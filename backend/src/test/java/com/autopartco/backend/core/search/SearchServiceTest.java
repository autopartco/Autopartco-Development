package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Sin BD: se verifica el SQL generado ({@link SearchSql}) y sus parámetros, y el flujo de {@link SearchService}
 * (respaldo por trigramas, hidratación de la página, facetas, validaciones) con un {@code JdbcClient} falso.
 */
class SearchServiceTest {
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final UUID MANUFACTURER = UUID.randomUUID();
    private static final BigDecimal LAT = new BigDecimal("4.65");
    private static final BigDecimal LNG = new BigDecimal("-74.08");

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final SearchDictionary dictionary = mock(SearchDictionary.class);
    private SearchService service;

    @BeforeEach
    void setUp() {
        when(dictionary.vocabulary()).thenReturn(QueryParserTest.vocabulary());
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries);
    }

    private static SearchCriteria criteria(SearchQuery query) {
        QueryParser parser = new QueryParser(mock(SearchDictionary.class, invocation -> SearchDictionary.Vocabulary.EMPTY));
        return SearchCriteria.of(query, parser.parse(query.q(), query.interpretOrDefault()));
    }

    // --- SQL ---------------------------------------------------------------------------------------------------

    @Test
    void radiusUsesACoordinateBoxThenHaversineWithParameters() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .lat(LAT).lng(LNG).radiusKm(10).build()), 24, 0);

        assertThat(statement.sql())
                .contains("sl.latitude between :south and :north", "sl.longitude between :west and :east",
                        "asin(sqrt(", "<= :radiusKm", "* (1.0 / (1.0 + coalesce(distance_km, 0) / 25.0))")
                .doesNotContain("4.65", "-74.08");
        Map<String, Object> params = statement.params();
        assertThat((double) params.get("south")).isCloseTo(4.65 - 10 / 111.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat((double) params.get("north")).isCloseTo(4.65 + 10 / 111.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat((double) params.get("west")).isLessThan(-74.08);
        assertThat((double) params.get("east")).isGreaterThan(-74.08);
        assertThat(params).containsEntry("radiusKm", 10.0).containsEntry("lat", 4.65).containsEntry("lng", -74.08)
                .containsEntry("limit", 24).containsEntry("offset", 0L);
    }

    @Test
    void locationWithoutRadiusOnlyComputesDistance() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .lat(LAT).lng(LNG).build()), 24, 48);

        assertThat(statement.sql()).contains("as distance_km").doesNotContain(":radiusKm", ":south");
        assertThat(statement.params()).containsEntry("offset", 48L).doesNotContainKey("radiusKm");
    }

    @Test
    void nearestPicksTheClosestListingPerProductAndOrdersByDistance() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .lat(LAT).lng(LNG).sort(SearchSort.NEAREST).build()), 24, 0);

        assertThat(statement.sql())
                .contains("select distinct on (product_id) *",
                        "order by product_id, distance_km asc nulls last, price asc, listing_key",
                        "order by distance_km asc nulls last, product_id");
    }

    @Test
    void onlyOneListingPerProductTheCheapestWithinTheFilters() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .q("pastillas").categoryId(CATEGORY).priceMax(new BigDecimal("90000")).build()), 24, 0);

        String sql = statement.sql();
        assertThat(sql).contains("select distinct on (product_id) *",
                "order by product_id, price asc, distance_km asc nulls last, listing_key",
                "count(*) over () as total", "limit :limit offset :offset", "order by score desc, product_id");
        // Los filtros van dentro de "matched", antes de elegir el listado por producto.
        assertThat(sql.indexOf("sl.price <= :priceMax")).isLessThan(sql.indexOf("distinct on"));
        assertThat(sql.indexOf("sl.category_path @> array[cast(:categoryId as uuid)]"))
                .isLessThan(sql.indexOf("distinct on"));
        assertThat(sql).contains("sl.document @@ to_tsquery('public.spanish_unaccent'::regconfig, :tsq)",
                "ts_rank_cd(sl.document, to_tsquery('public.spanish_unaccent'::regconfig, :tsq))",
                "(1 + 0.3 * (case when stock > 0 then 1 else 0 end))", "(1 + 0.1 * coalesce(store_rating, 0) / 5)");
        assertThat(statement.params()).containsEntry("tsq", "pastillas:*").containsEntry("categoryId", CATEGORY)
                .containsEntry("priceMax", new BigDecimal("90000"));
    }

    @Test
    void valuesNeverAppearInTheSqlText() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .q("x'); drop table products; --").cityCode("11001").departmentCode("11").minRating(4)
                .manufacturerIds(List.of(MANUFACTURER)).storeId(UUID.randomUUID()).build()), 24, 0);

        assertThat(statement.sql()).doesNotContain("drop", "11001", MANUFACTURER.toString())
                .contains("sl.manufacturer_id in (:manufacturerIds)", "sl.city_code = :cityCode",
                        "sl.department_code = :departmentCode", "sl.store_rating >= :minRating");
        assertThat(statement.params()).containsEntry("manufacturerIds", List.of(MANUFACTURER));
    }

    @Test
    void vehicleFilterMatchesUniversalOrCoveringRulesAtEachLevel() {
        UUID make = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        UUID version = UUID.randomUUID();

        assertThat(SearchSql.page(criteria(SearchQuery.builder().makeId(make).build()), 24, 0).sql())
                .contains("(sl.universal or sl.vehicle_make_ids @> array[cast(:makeId as uuid)])");
        assertThat(SearchSql.page(criteria(SearchQuery.builder().makeId(make).modelId(model).build()), 24, 0).sql())
                .contains("(sl.universal or sl.vehicle_model_ids && array[cast(:modelId as uuid), cast(:makeId as uuid)])");
        SearchSql.Statement byVersion = SearchSql.page(criteria(SearchQuery.builder()
                .makeId(make).modelId(model).versionId(version).year(2015).build()), 24, 0);
        assertThat(byVersion.sql()).contains("sl.vehicle_version_ids && array[cast(:versionId as uuid), "
                + "cast(:modelId as uuid), cast(:makeId as uuid)]",
                "(sl.year_from is null or sl.year_from <= :year)", "(sl.year_to is null or sl.year_to >= :year)");
        assertThat(byVersion.params()).containsEntry("versionId", version).containsEntry("modelId", model)
                .containsEntry("makeId", make).containsEntry("year", 2015);
    }

    @Test
    void facetsExcludeTheirOwnFilterButKeepTheOthers() {
        SearchSql.Statement statement = SearchSql.facets(criteria(SearchQuery.builder()
                .q("pastillas").categoryId(CATEGORY).manufacturerIds(List.of(MANUFACTURER))
                .priceMin(new BigDecimal("1000")).onSale(true).build()));

        String sql = statement.sql();
        String baseWhere = sql.substring(sql.indexOf("from search_listings sl"), sql.indexOf("), grouped as"));
        assertThat(baseWhere).contains("sl.document @@").doesNotContain("category_path", "manufacturer_id in",
                "sl.price >=", "has_promotion");
        assertThat(sql).contains("(sl.category_path @> array[cast(:categoryId as uuid)]) as f_category",
                "(sl.manufacturer_id in (:manufacturerIds)) as f_manufacturer",
                "(sl.price >= :priceMin) as f_price", "(sl.has_promotion) as f_sale", "(true) as f_city",
                "group by grouping sets ((category_id), (manufacturer_id), (city_code), ())",
                "count(distinct product_id) filter (where f_manufacturer and f_city and f_price and f_stock and f_sale) as n_category",
                "count(distinct product_id) filter (where f_category and f_city and f_price and f_stock and f_sale) as n_manufacturer",
                "min(price) filter (where f_category and f_manufacturer and f_city and f_stock and f_sale) as price_min",
                "filter (where f_category and f_manufacturer and f_city and f_price and f_stock and has_promotion) as n_on_sale");
        assertThat(statement.params()).containsEntry("categoryId", CATEGORY).containsEntry("priceMin",
                new BigDecimal("1000"));
    }

    @Test
    void referenceSearchesTheWeightAOfTheDocument() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder().q("BKR6E-11").build()), 24, 0);

        assertThat(statement.sql()).contains("sl.document @@ plainto_tsquery('public.spanish_unaccent'::regconfig, :reference)",
                "ts_rank('{0, 0, 0, 1}'::float4[], sl.document");
        assertThat(statement.params()).containsEntry("reference", "BKR6E-11").doesNotContainKey("tsq");
    }

    // --- Flujo ---------------------------------------------------------------------------------------------------

    @Test
    void withNoExactResultsRetriesWithTrigramsAndFlagsFuzzy() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("<% sl.search_text") && sql.contains("limit :limit"),
                call -> List.of(row(product, branch, null, 1)));
        ProductResponse listing = listing(product, branch);
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(product, branch))), any()))
                .thenReturn(List.of(listing));

        SearchResult result = service.search(SearchQuery.builder().q("pastilas").facets(false).build(),
                BuyerContext.anonymous());

        assertThat(result.fuzzy()).isTrue();
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting(SearchResult.Hit::listing).containsExactly(listing);
        assertThat(jdbc.callsMatching(sql -> sql.equals(SearchSql.FUZZY_THRESHOLD_SQL))).hasSize(1);
        SearchFakeJdbc.Call fuzzyCall = jdbc.callsMatching(sql -> sql.contains("<% sl.search_text")).getFirst();
        assertThat(fuzzyCall.params()).containsEntry("fuzzy", "pastilas").doesNotContainKey("tsq");
    }

    @Test
    void withResultsThereIsNoFuzzyRetry() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(product, branch, null, 3)));
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));

        SearchResult result = service.search(SearchQuery.builder().q("pastillas").facets(false).build(), null);

        assertThat(result.fuzzy()).isFalse();
        assertThat(result.total()).isEqualTo(3);
        assertThat(jdbc.callsMatching(sql -> sql.contains("<%"))).isEmpty();
    }

    @Test
    void withNoResultsAtAllStaysNotFuzzyAndHydratesNothing() {
        SearchResult result = service.search(SearchQuery.builder().q("zzzz").facets(false).build(), null);

        assertThat(result.fuzzy()).isFalse();
        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        verify(listingQueries, never()).publishedListingsByKeys(anyList(), any());
    }

    @Test
    void hydratesOnlyThePageKeepingOrderAndRoundingDistance() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(first, branch, 3.24, 3),
                row(gone, branch, 4.0, 3), row(second, branch, 12.06, 3)));
        BuyerContext buyer = new BuyerContext(UUID.randomUUID(), java.util.Set.of("FREQUENT"), Map.of());
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(first, branch),
                new ListingKey(gone, branch), new ListingKey(second, branch))), eq(buyer)))
                .thenReturn(List.of(listing(first, branch), listing(second, branch)));

        SearchResult result = service.search(SearchQuery.builder().lat(LAT).lng(LNG).sort(SearchSort.NEAREST)
                .facets(false).build(), buyer);

        assertThat(result.items()).extracting(hit -> hit.listing().id()).containsExactly(first, second);
        assertThat(result.items()).extracting(SearchResult.Hit::distanceKm)
                .containsExactly(new BigDecimal("3.2"), new BigDecimal("12.1"));
    }

    @Test
    void interpretedFiltersApplyUnlessTheClientSetsThemOrDisablesInterpretation() {
        service.search(SearchQuery.builder().q("pastillas spark 2015 bogota").facets(false).build(), null);
        SearchFakeJdbc.Call interpreted = lastExactPage();
        assertThat(interpreted.params())
                .containsEntry("modelId", QueryParserTest.SPARK.id())
                .containsEntry("makeId", QueryParserTest.CHEVROLET.id())
                .containsEntry("year", 2015)
                .containsEntry("cityCode", "11001")
                .containsEntry("tsq", "pastillas:*");

        UUID otherMake = UUID.randomUUID();
        service.search(SearchQuery.builder().q("pastillas spark").makeId(otherMake).year(2010).facets(false)
                .build(), null);
        SearchFakeJdbc.Call explicit = lastExactPage();
        assertThat(explicit.params()).containsEntry("makeId", otherMake).containsEntry("year", 2010)
                .doesNotContainKey("modelId");

        SearchResult plain = service.search(SearchQuery.builder().q("pastillas spark 2015").interpret(false)
                .facets(false).build(), null);
        SearchFakeJdbc.Call literal = lastExactPage();
        assertThat(literal.params()).doesNotContainKeys("makeId", "modelId", "year")
                .containsEntry("tsq", "pastillas & spark & 2015:*");
        assertThat(plain.interpreted()).isNull();
    }

    @Test
    void facetsAreReadPerGroupCappedAt20AndOnlyOnTheFirstPageByDefault() {
        jdbc.rows(sql -> sql.contains("grouping sets"), call -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                rows.add(facetRow(0, 1, 1, Map.of("category_id", UUID.randomUUID(), "category_name", "C" + i,
                        "n_category", (long) i + 1)));
            }
            rows.add(facetRow(1, 0, 1, Map.of("manufacturer_id", MANUFACTURER, "manufacturer_name", "Bosch",
                    "n_manufacturer", 7L)));
            rows.add(facetRow(1, 1, 0, Map.of("city_code", "11001", "city_name", "Bogotá, D.C.", "n_city", 5L)));
            rows.add(facetRow(1, 1, 0, Map.of("city_code", "05001", "n_city", 0L)));
            rows.add(facetRow(1, 1, 1, Map.of("price_min", new BigDecimal("18000"),
                    "price_max", new BigDecimal("420000"), "n_in_stock", 280L, "n_on_sale", 41L)));
            return rows;
        });

        SearchResult.Facets facets = service.search(SearchQuery.builder().q("frenos").build(), null).facets();

        assertThat(facets.categories()).hasSize(20).first().satisfies(top -> assertThat(top.count()).isEqualTo(25));
        assertThat(facets.manufacturers()).containsExactly(new SearchResult.IdCount(MANUFACTURER, "Bosch", 7));
        assertThat(facets.cities()).containsExactly(new SearchResult.CityCount("11001", "Bogotá, D.C.", 5));
        assertThat(facets.price()).isEqualTo(new SearchResult.PriceRange(new BigDecimal("18000"),
                new BigDecimal("420000")));
        assertThat(facets.inStock()).isEqualTo(280);
        assertThat(facets.onSale()).isEqualTo(41);
        assertThat(service.search(SearchQuery.builder().q("frenos").page(1).build(), null).facets()).isNull();
    }

    @Test
    void emptyPageBeyondTheEndCountsSeparately() {
        jdbc.scalar(sql -> sql.startsWith("select count(distinct sl.product_id)"), 30L);

        SearchResult result = service.search(SearchQuery.builder().page(5).size(24).build(), null);

        assertThat(result.total()).isEqualTo(30);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void invalidParametersAreRejectedWith400() {
        assertCode(SearchQuery.builder().size(49).build(), "SEARCH_SIZE_INVALID");
        assertCode(SearchQuery.builder().size(0).build(), "SEARCH_SIZE_INVALID");
        assertCode(SearchQuery.builder().page(-1).build(), "SEARCH_PAGE_INVALID");
        assertCode(SearchQuery.builder().lat(LAT).lng(LNG).radiusKm(0).build(), "SEARCH_RADIUS_INVALID");
        assertCode(SearchQuery.builder().lat(LAT).lng(LNG).radiusKm(201).build(), "SEARCH_RADIUS_INVALID");
        assertCode(SearchQuery.builder().sort(SearchSort.NEAREST).build(), "LOCATION_REQUIRED");
        assertCode(SearchQuery.builder().radiusKm(10).build(), "LOCATION_REQUIRED");
        assertCode(SearchQuery.builder().lat(LAT).build(), "SEARCH_POINT_INCOMPLETE");
        assertCode(SearchQuery.builder().lat(new BigDecimal("40.4")).lng(new BigDecimal("-3.7")).build(),
                "SEARCH_POINT_OUTSIDE_COVERAGE");
        assertCode(SearchQuery.builder().priceMin(new BigDecimal("10")).priceMax(new BigDecimal("5")).build(),
                "SEARCH_PRICE_RANGE_INVALID");
        assertCode(SearchQuery.builder().minRating(6).build(), "SEARCH_RATING_INVALID");
        assertCode(SearchQuery.builder().cityCode("bogota").build(), "SEARCH_PLACE_INVALID");
        assertCode(SearchQuery.builder().modelId(UUID.randomUUID()).build(), "VEHICLE_FILTER_INVALID");
        assertThatThrownBy(() -> SearchSort.fromParameter("cheapest"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SEARCH_SORT_INVALID"));
        assertThat(SearchSort.fromParameter("price_asc")).isEqualTo(SearchSort.PRICE_ASC);
        assertThat(jdbc.calls).isEmpty();
    }

    @Test
    void catalogAcceptsLargerPagesThanThePublicSearch() {
        service.catalog(SearchQuery.builder().size(500).facets(false).build(), null);

        assertThat(jdbc.callsMatching(sql -> sql.contains("limit :limit")).getFirst().params())
                .containsEntry("limit", 500);
    }

    /** Última consulta de página exacta (sin el reintento por trigramas). */
    private SearchFakeJdbc.Call lastExactPage() {
        return jdbc.callsMatching(sql -> sql.contains("limit :limit") && !sql.contains("<%")).getLast();
    }

    private void assertCode(SearchQuery query, String code) {
        assertThatThrownBy(() -> service.search(query, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private static Map<String, Object> row(UUID product, UUID branch, Double distance, long total) {
        Map<String, Object> row = new HashMap<>();
        row.put("product_id", product);
        row.put("branch_id", branch);
        row.put("distance_km", distance);
        row.put("total", total);
        return row;
    }

    private static Map<String, Object> facetRow(int category, int manufacturer, int city, Map<String, Object> values) {
        Map<String, Object> row = new HashMap<>(values);
        row.put("g_category", category);
        row.put("g_manufacturer", manufacturer);
        row.put("g_city", city);
        return row;
    }

    private static ProductResponse listing(UUID product, UUID branch) {
        return new ProductResponse(product, UUID.randomUUID(), branch, "Sede", "Pastillas", "SKU", null, null,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false, CATEGORY, "ACTIVE",
                false, Map.of(), List.of(), List.of());
    }
}
