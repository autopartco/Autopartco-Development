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

import com.autopartco.backend.core.advertising.AdContext;
import com.autopartco.backend.core.advertising.AdPick;
import com.autopartco.backend.core.advertising.AdPlacement;
import com.autopartco.backend.core.advertising.AdServer;
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
                        "asin(sqrt(", "<= :radiusKm",
                        "coalesce(store_rating, 0) desc, (1.0 / (1.0 + coalesce(distance_km, 0) / 25.0)) desc, product_id")
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
                "count(*) over () as total", "limit :limit offset :offset");
        // Los filtros van en los candidatos, antes del tope y de elegir el listado por producto.
        assertThat(sql.indexOf("sl.price <= :priceMax")).isLessThan(sql.indexOf("limit 500"));
        assertThat(sql.indexOf("sl.category_path @> array[cast(:categoryId as uuid)]"))
                .isLessThan(sql.indexOf("limit 500"));
        assertThat(sql.indexOf("limit 500")).isLessThan(sql.indexOf("distinct on"));
        assertThat(sql).contains(
                "sl.document @@ (select to_tsquery('public.spanish_unaccent'::regconfig, :candTsq))",
                "ts_rank_cd(sl.document, (select to_tsquery('public.spanish_unaccent'::regconfig, :candTsq)))",
                "order by text_rank desc, sl.listing_key\n    limit 500");
        assertThat(statement.params()).containsEntry("candTsq", "pastillas:*").containsEntry("t0", "pastillas:*")
                .containsEntry("categoryId", CATEGORY).containsEntry("priceMax", new BigDecimal("90000"))
                .doesNotContainKey("tsq");
    }

    @Test
    void relevanceIsLexicographicCoverageReferenceVehicleYearRankStockRating() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder().q("pastillas").build()), 24, 0);

        assertThat(statement.sql()).contains("order by cov_tier desc, ref_exact desc, vehicle_level desc, "
                + "year_ok desc, city_ok desc, text_rank desc, (stock > 0) desc, coalesce(store_rating, 0) desc, "
                + "product_id").doesNotContain("as score", "hint_boost", "* (1 +");
    }

    @Test
    void coverageNeedsEveryWordUpToTwoAndSeventyPercentAfter() {
        assertThat(SearchSql.minimumCovered(3, 70)).isEqualTo(3);
        assertThat(SearchSql.minimumCovered(4, 70)).isEqualTo(3);
        assertThat(SearchSql.minimumCovered(10, 70)).isEqualTo(7);
        assertThat(SearchSql.minimumCovered(7, 85)).isEqualTo(6);

        SearchSql.Statement two = SearchSql.page(criteria(SearchQuery.builder().q("bomba agua").build()), 24, 0);
        assertThat(two.params()).containsEntry("covFull", 2).containsEntry("covHigh", 2).containsEntry("covMin", 2);
        SearchSql.Statement four = SearchSql.page(criteria(SearchQuery.builder().q("bomba agua motor sintetico")
                .build()), 24, 0);
        assertThat(four.params()).containsEntry("covFull", 4).containsEntry("covHigh", 4).containsEntry("covMin", 3)
                .containsEntry("t0", "bomba").containsEntry("t3", "sintetico:*");
        assertThat(four.sql()).contains("where ref_exact or covered >= :covMin",
                "(case when ref_exact or covered >= :covFull then 3 when covered >= :covHigh then 2 else 1 end)",
                "(case when sl.document @@ (select to_tsquery('public.spanish_unaccent'::regconfig, :t3)) then 1 "
                        + "else 0 end)");
    }

    @Test
    void fillerWordsDoNotCountAsContent() {
        SearchCriteria criteria = criteria(SearchQuery.builder().q("necesito un filtro de aire original").build());

        assertThat(criteria.terms()).containsExactly("filtro", "aire");
        assertThat(criteria.tsQuery()).contains("necesito", "original");
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

        assertThat(statement.sql()).contains(
                "sl.document @@ (select plainto_tsquery('public.spanish_unaccent'::regconfig, :reference))",
                "ts_rank('{0, 0, 0, 1}'::float4[], sl.document").doesNotContain("limit 500", ":candTsq");
        assertThat(statement.params()).containsEntry("reference", "BKR6E-11").doesNotContainKey("tsq");
    }

    @Test
    void referenceWithWordsIsACandidateAndACoverageUnitAndExactCountsAsFull() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder().q("bomba BKR6E-11").build()),
                24, 0);

        assertThat(statement.sql()).contains("sl.document @@ (select to_tsquery('public.spanish_unaccent'::regconfig, "
                + ":candTsq) || plainto_tsquery('public.spanish_unaccent'::regconfig, :reference))",
                "(case when sl.document @@ (select plainto_tsquery('public.spanish_unaccent'::regconfig, :reference)) "
                        + "then 1 else 0 end)",
                "ts_rank('{0, 0, 0, 1}'::float4[], sl.document, (select plainto_tsquery(");
        assertThat(statement.params()).containsEntry("candTsq", "bomba:*").containsEntry("reference", "BKR6E-11")
                .containsEntry("covFull", 2).containsEntry("covMin", 2);
    }

    @Test
    void productRatingFilterAndSortUseTheIndexColumns() {
        SearchSql.Statement statement = SearchSql.page(criteria(SearchQuery.builder()
                .minProductRating(4).sort(SearchSort.PRODUCT_RATING).build()), 24, 0);

        assertThat(statement.sql()).contains("sl.product_rating >= :minProductRating",
                "sl.product_rating, sl.product_review_count",
                "order by product_rating desc nulls last, product_review_count desc, cov_tier desc");
        assertThat(statement.params()).containsEntry("minProductRating", 4);
        assertThat(SearchSql.count(criteria(SearchQuery.builder().minProductRating(3).build())).sql())
                .contains("sl.product_rating >= :minProductRating");
        assertThat(SearchSort.fromParameter("product_rating")).isEqualTo(SearchSort.PRODUCT_RATING);
    }

    @Test
    void invalidMinProductRatingIsBadRequest() {
        assertCode(SearchQuery.builder().minProductRating(0).build(), "SEARCH_RATING_INVALID");
        assertCode(SearchQuery.builder().minProductRating(6).build(), "SEARCH_RATING_INVALID");
    }

    @Test
    void hitsCarryTheProductRatingFromTheIndex() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> {
            Map<String, Object> row = row(product, branch, null, 1);
            row.put("product_rating", new BigDecimal("4.7"));
            row.put("product_review_count", 128);
            return List.of(row);
        });
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));

        SearchResult result = service.search(SearchQuery.builder().facets(false).build(), null);

        ProductResponse hit = result.items().getFirst().listing();
        assertThat(hit.rating()).isEqualByComparingTo("4.7");
        assertThat(hit.reviewCount()).isEqualTo(128);
    }

    @Test
    void hitsWithoutReviewsHaveNoRating() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(product, branch, null, 1)));
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));

        ProductResponse hit = service.search(SearchQuery.builder().facets(false).build(), null).items().getFirst()
                .listing();

        assertThat(hit.rating()).isNull();
        assertThat(hit.reviewCount()).isZero();
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
        // El respaldo busca el texto ya corregido («pastilas» → «pastillas», palabra del vocabulario).
        assertThat(fuzzyCall.params()).containsEntry("fuzzy", "pastillas").doesNotContainKey("tsq");
    }

    @Test
    void oneStatementWithoutCascadeCoversTheContentWords() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(product, branch, null, 5)));
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));

        SearchResult result = service.search(SearchQuery.builder().q("bomba de freno para suzuki").facets(false)
                .build(), null);

        assertThat(result.fuzzy()).isFalse();
        assertThat(result.total()).isEqualTo(5);
        assertThat(jdbc.calls).hasSize(1);
        SearchFakeJdbc.Call page = jdbc.calls.getFirst();
        assertThat(page.params()).containsEntry("candTsq", "bomba | freno").containsEntry("t0", "bomba")
                .containsEntry("t1", "freno").containsEntry("covMin", 2)
                .containsEntry("hintMakeId", QueryParserTest.SUZUKI.id()).doesNotContainKeys("tsq", "anyTsq");
        assertThat(page.sql()).doesNotContain("<%", ":anyTsq");
    }

    @Test
    void whenNothingCoversTheWordsTheTrigramsRunOnceAsTheLastResort() {
        service.search(SearchQuery.builder().q("bomba de freno").facets(false).build(), null);

        List<SearchFakeJdbc.Call> pages = jdbc.callsMatching(sql -> sql.contains("limit :limit"));
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).params()).containsKey("candTsq");
        assertThat(pages.get(1).sql()).contains(":fuzzy <% sl.search_text").doesNotContain(":candTsq");
        assertThat(jdbc.callsMatching(sql -> sql.equals(SearchSql.FUZZY_THRESHOLD_SQL))).hasSize(1);
    }

    @Test
    void theInterpretationCarriesTheCorrections() {
        SearchResult result = service.search(SearchQuery.builder().q("pastilas spark").facets(false).build(), null);

        assertThat(result.interpreted().corrections())
                .containsExactly(new ParsedQuery.Correction("pastilas", "pastillas"));
        assertThat(result.interpreted().relaxed()).isEqualTo(SearchInterpretation.Relaxed.NONE);
    }

    @Test
    void withAnInterpretedMakeAndNoCompatibleResultTheVehicleIsRelaxed() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> {
            Map<String, Object> row = row(product, branch, null, 1);
            row.put("best_level", 1);
            return List.of(row);
        });

        assertThat(service.search(SearchQuery.builder().q("pastillas suzuki").facets(false).build(), null)
                .interpreted().relaxed()).isEqualTo(SearchInterpretation.Relaxed.VEHICLE);
    }

    @Test
    void withAResultThatMentionsTheMakeNothingIsRelaxed() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> {
            Map<String, Object> row = row(product, branch, null, 1);
            row.put("best_level", 2);
            return List.of(row);
        });

        assertThat(service.search(SearchQuery.builder().q("pastillas suzuki").facets(false).build(), null)
                .interpreted().relaxed()).isEqualTo(SearchInterpretation.Relaxed.NONE);
        // Sin marca interpretada nunca se relaja, aunque el nivel sea 0.
        assertThat(service.search(SearchQuery.builder().q("pastillas").facets(false).build(), null)
                .interpreted()).isNull();
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
    void sponsoredAddsSearchTopAdsWithoutRepeatingOrganicProducts() {
        UUID organic = UUID.randomUUID();
        UUID advertised = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        AdServer adServer = mock(AdServer.class);
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, adServer);
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(organic, branch, null, 1)));
        jdbc.rows(sql -> sql.contains("distinct on (product_id) product_id, branch_id"),
                call -> List.of(Map.of("product_id", advertised, "branch_id", branch),
                        Map.of("product_id", unpublished, "branch_id", branch)));
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(organic, branch))), any()))
                .thenReturn(List.of(listing(organic, branch)));
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(advertised, branch),
                new ListingKey(unpublished, branch))), any())).thenReturn(List.of(listing(advertised, branch)));
        when(adServer.pick(eq(AdPlacement.SEARCH_TOP), any(), eq(2))).thenReturn(List.of(
                new AdPick(UUID.randomUUID(), advertised, AdPlacement.SEARCH_TOP, "tok-1"),
                new AdPick(UUID.randomUUID(), unpublished, AdPlacement.SEARCH_TOP, "tok-2")));

        SearchResult result = service.search(SearchQuery.builder().q("pastillas").categoryId(CATEGORY)
                .facets(false).build(), null, "s:abc");

        assertThat(result.items()).extracting(hit -> hit.listing().id()).containsExactly(organic);
        assertThat(result.sponsored()).hasSize(1);
        assertThat(result.sponsored().getFirst().hit().listing().id()).isEqualTo(advertised);
        assertThat(result.sponsored().getFirst().adImpressionToken()).isEqualTo("tok-1");
        org.mockito.ArgumentCaptor<AdContext> context = org.mockito.ArgumentCaptor.forClass(AdContext.class);
        verify(adServer).pick(eq(AdPlacement.SEARCH_TOP), context.capture(), eq(2));
        assertThat(context.getValue().excludedProductIds()).containsExactly(organic);
        assertThat(context.getValue().text()).isEqualTo("pastillas");
        assertThat(context.getValue().categoryId()).isEqualTo(CATEGORY);
        assertThat(context.getValue().visitorKey()).isEqualTo("s:abc");
    }

    @Test
    void withoutCampaignsOrInTheCatalogThereAreNoSponsoredResults() {
        AdServer adServer = mock(AdServer.class);
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, adServer);
        when(adServer.pick(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        assertThat(service.search(SearchQuery.builder().q("pastillas").facets(false).build(), null, "ip:1")
                .sponsored()).isEmpty();
        assertThat(service.catalog(SearchQuery.builder().q("pastillas").facets(false).build(), null).sponsored())
                .isEmpty();
        assertThat(service.search(SearchQuery.builder().q("pastillas").storeId(UUID.randomUUID()).facets(false)
                .build(), null, "ip:1").sponsored()).isEmpty();
        verify(adServer, org.mockito.Mockito.times(1)).pick(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(jdbc.callsMatching(sql -> sql.contains("product_id, branch_id\n            from search_listings")))
                .isEmpty();
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
    void interpretedMakeOnlyExcludesOtherVehiclesWithTheIndexArrays() {
        SearchResult result = service.search(SearchQuery.builder().q("bomba de freno para suzuki").facets(false)
                .build(), null);
        SearchFakeJdbc.Call page = lastExactPage();
        String level = "(case when sl.vehicle_make_ids @> array[cast(:hintMakeId as uuid)] then 3\n"
                + "                 when cardinality(sl.vehicle_make_ids) = 0 and sl.mentioned_make_ids @> "
                + "array[cast(:hintMakeId as uuid)] then 2\n"
                + "                 when sl.universal or (cardinality(sl.vehicle_make_ids) = 0 and "
                + "cardinality(sl.mentioned_make_ids) = 0) then 1\n"
                + "                 else 0 end)";

        assertThat(page.sql())
                .doesNotContain("(sl.universal or sl.vehicle_make_ids @> array[cast(:makeId as uuid)])")
                .contains(level + " > 0", level + " as vehicle_level")
                .doesNotContain(":vehicleTsq", "hint_boost");
        // La exclusión va en los candidatos, antes del tope.
        assertThat(page.sql().indexOf(level + " > 0")).isLessThan(page.sql().indexOf("limit 500"));
        assertThat(page.params()).doesNotContainKeys("makeId", "vehicleTsq")
                .containsEntry("hintMakeId", QueryParserTest.SUZUKI.id());
        assertThat(result.interpreted().makeId()).isEqualTo(QueryParserTest.SUZUKI.id());
    }

    @Test
    void interpretedModelYearRangeAndCityRankButDoNotFilter() {
        service.search(SearchQuery.builder().q("pastillas spark 2010-2015 bogota").facets(false).build(), null);
        SearchFakeJdbc.Call interpreted = lastExactPage();
        assertThat(interpreted.params())
                .doesNotContainKeys("makeId", "modelId", "year", "cityCode", "modelTsq", "vehicleTsq")
                .containsEntry("hintModelId", QueryParserTest.SPARK.id())
                .containsEntry("hintMakeId", QueryParserTest.CHEVROLET.id())
                .containsEntry("hintYearFrom", 2010)
                .containsEntry("hintYearTo", 2015)
                .containsEntry("hintCityCode", "11001")
                .containsEntry("candTsq", "pastillas:*");
        assertThat(interpreted.sql()).doesNotContain("sl.city_code = :cityCode").doesNotContain(":year)")
                .contains("(case when sl.city_code = :hintCityCode then 1 else 0 end) as city_ok")
                .contains("coalesce(sl.year_from, :hintYearTo) <= :hintYearTo"
                        + " and coalesce(sl.year_to, :hintYearFrom) >= :hintYearFrom then 1 else 0 end) as year_ok")
                .contains("(case when sl.vehicle_model_ids @> array[cast(:hintModelId as uuid)] then 4\n"
                        + "                 when sl.vehicle_model_ids @> array[cast(:hintMakeId as uuid)] or "
                        + "sl.mentioned_model_ids @> array[cast(:hintModelId as uuid)] then 3");
    }

    @Test
    void explicitParametersStillFilter() {
        UUID make = QueryParserTest.SUZUKI.id();
        service.search(SearchQuery.builder().q("bomba de freno para suzuki").makeId(make).year(2018)
                .cityCode("05001").facets(false).build(), null);
        SearchFakeJdbc.Call page = lastExactPage();

        assertThat(page.sql()).contains("(sl.universal or sl.vehicle_make_ids @> array[cast(:makeId as uuid)])")
                .contains("sl.city_code = :cityCode").contains("(sl.year_to is null or sl.year_to >= :year)")
                .doesNotContain(":hintMakeId").doesNotContain(":vehicleTsq");
        assertThat(page.params()).containsEntry("makeId", make).containsEntry("year", 2018)
                .containsEntry("cityCode", "05001").doesNotContainKeys("hintYear", "hintCityCode");
    }

    @Test
    void interpretedValuesThatTheClientOverridesAreIgnored() {
        UUID otherMake = UUID.randomUUID();
        service.search(SearchQuery.builder().q("pastillas spark").makeId(otherMake).year(2010).facets(false)
                .build(), null);
        SearchFakeJdbc.Call explicit = lastExactPage();
        assertThat(explicit.params()).containsEntry("makeId", otherMake).containsEntry("year", 2010)
                .doesNotContainKeys("modelId", "hintModelId", "hintMakeId");

        SearchResult plain = service.search(SearchQuery.builder().q("pastillas spark 2015").interpret(false)
                .facets(false).build(), null);
        SearchFakeJdbc.Call literal = lastExactPage();
        assertThat(literal.params()).doesNotContainKeys("makeId", "modelId", "year", "hintMakeId")
                .containsEntry("candTsq", "pastillas | spark | 2015:*");
        assertThat(plain.interpreted()).isNull();
    }

    @Test
    void facetsComeInThePageStatementWithoutFacetFiltersCappedAt20AndOnlyOnTheFirstPageByDefault() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));
        jdbc.rows(sql -> sql.contains("grouping sets"), call -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row(product, branch, null, 300));
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

        SearchResult result = service.search(SearchQuery.builder().q("frenos").build(), null);
        SearchResult.Facets facets = result.facets();

        assertThat(jdbc.calls).hasSize(1);
        assertThat(jdbc.calls.getFirst().sql()).contains("0 as part", "from kept\n    group by grouping sets",
                "where ord > :offset and ord <= :offset + :limit", "order by part, ord");
        assertThat(result.total()).isEqualTo(300);
        assertThat(result.items()).hasSize(1);
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
    void withAFacetFilterTheFacetsHaveTheirOwnStatementOverTheSameCandidates() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(product, branch, null, 1)));

        SearchResult result = service.search(SearchQuery.builder().q("frenos").inStock(true).build(), null);

        assertThat(result.facets()).isNotNull();
        assertThat(jdbc.calls).hasSize(2);
        String facets = jdbc.calls.get(1).sql();
        assertThat(facets).contains("group by grouping sets", "from kept", "limit 500", "(sl.stock > 0) as f_stock")
                .contains("where ref_exact or covered >= :covMin");
        assertThat(facets.indexOf("(sl.stock > 0) as f_stock")).isLessThan(facets.indexOf("from search_listings"));
    }

    @Test
    void withoutResultsOrFacetFiltersTheFacetsAreEmptyWithoutAnotherStatement() {
        SearchResult result = service.search(SearchQuery.builder().q("zzzz").facets(true).build(), null);

        assertThat(result.facets()).isEqualTo(SearchService.EMPTY_FACETS);
        assertThat(jdbc.callsMatching(sql -> sql.contains("grouping sets") && !sql.contains("as part"))).isEmpty();
    }

    @Test
    void emptyPageBeyondTheEndCountsSeparately() {
        jdbc.scalar(sql -> sql.contains("select count(distinct product_id) from kept"), 30L);

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
        return jdbc.callsMatching(sql -> sql.contains("limit :limit") && !sql.contains("<%") && !sql.contains(":anyTsq"))
                .getLast();
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
        row.put("part", 1);
        row.put("g_category", category);
        row.put("g_manufacturer", manufacturer);
        row.put("g_city", city);
        return row;
    }

    @Test
    void categoryAdsUseTheCategoryAndItsSubcategoriesAndHydrateListings() {
        UUID sub = UUID.randomUUID();
        UUID advertised = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        AdServer adServer = mock(AdServer.class);
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, adServer);
        jdbc.rows(sql -> sql.contains("category_path @> array"), call -> List.of(Map.of("category_id", sub)));
        jdbc.rows(sql -> sql.contains("distinct on (product_id) product_id, branch_id"),
                call -> List.of(Map.of("product_id", advertised, "branch_id", branch)));
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(advertised, branch))), any()))
                .thenReturn(List.of(listing(advertised, branch)));
        when(adServer.pick(eq(AdPlacement.CATEGORY_TOP), any(), eq(3))).thenReturn(List.of(
                new AdPick(UUID.randomUUID(), advertised, AdPlacement.CATEGORY_TOP, "tok-c")));

        List<SearchResult.Sponsored> ads = service.categoryAds(CATEGORY, "s:abc", null);

        assertThat(ads).hasSize(1);
        assertThat(ads.getFirst().adImpressionToken()).isEqualTo("tok-c");
        org.mockito.ArgumentCaptor<AdContext> context = org.mockito.ArgumentCaptor.forClass(AdContext.class);
        verify(adServer).pick(eq(AdPlacement.CATEGORY_TOP), context.capture(), eq(3));
        assertThat(context.getValue().categoryScope()).containsExactlyInAnyOrder(CATEGORY, sub);
        assertThat(context.getValue().visitorKey()).isEqualTo("s:abc");
        assertThat(service.categoryAds(null, "s:abc", null)).isEmpty();
    }

    @Test
    void relatedAdsUseTheProductCategoryVehiclesAndExcludeItsStoreAndItself() {
        UUID product = UUID.randomUUID();
        UUID store = UUID.randomUUID();
        UUID make = UUID.randomUUID();
        UUID advertised = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        AdServer adServer = mock(AdServer.class);
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, adServer);
        jdbc.rows(sql -> sql.contains("select category_id, store_id, name"),
                call -> List.of(Map.of("category_id", CATEGORY, "store_id", store, "name", "Pastillas Bosch")));
        jdbc.rows(sql -> sql.contains("unnest(sl.vehicle_make_ids)"), call -> List.of(Map.of("make_id", make)));
        jdbc.rows(sql -> sql.contains("distinct on (product_id) product_id, branch_id"),
                call -> List.of(Map.of("product_id", advertised, "branch_id", branch)));
        when(listingQueries.publishedListingsByKeys(eq(List.of(new ListingKey(advertised, branch))), any()))
                .thenReturn(List.of(listing(advertised, branch)));
        when(adServer.pick(eq(AdPlacement.PRODUCT_RELATED), any(), eq(4))).thenReturn(List.of(
                new AdPick(UUID.randomUUID(), advertised, AdPlacement.PRODUCT_RELATED, "tok-r")));

        List<SearchResult.Sponsored> ads = service.relatedAds(product, null, BuyerContext.anonymous());

        assertThat(ads).extracting(SearchResult.Sponsored::adImpressionToken).containsExactly("tok-r");
        org.mockito.ArgumentCaptor<AdContext> context = org.mockito.ArgumentCaptor.forClass(AdContext.class);
        verify(adServer).pick(eq(AdPlacement.PRODUCT_RELATED), context.capture(), eq(4));
        assertThat(context.getValue().excludedProductIds()).containsExactly(product);
        assertThat(context.getValue().excludedStoreId()).isEqualTo(store);
        assertThat(context.getValue().categoryId()).isEqualTo(CATEGORY);
        assertThat(context.getValue().makeScope()).containsExactly(make);
        assertThat(context.getValue().text()).isEqualTo("pastillas bosch");
    }

    @Test
    void relatedAndCategoryAdsNeverFail() {
        AdServer adServer = mock(AdServer.class);
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, adServer);
        // Producto sin publicar: no se consulta al servidor de anuncios.
        assertThat(service.relatedAds(UUID.randomUUID(), "s:1", null)).isEmpty();
        verify(adServer, never()).pick(any(), any(), org.mockito.ArgumentMatchers.anyInt());

        when(adServer.pick(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("x"));
        assertThat(service.categoryAds(CATEGORY, "s:1", null)).isEmpty();
        assertThat(new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries)
                .categoryAds(CATEGORY, "s:1", null)).isEmpty();
    }

    // --- Identificador de búsqueda y aprendizaje de clics ----------------------------------------------------------

    @Test
    void searchIdIsNewOnEveryCallAndNeverComesFromTheResultCache() {
        UUID product = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of(row(product, branch, null, 1)));
        when(listingQueries.publishedListingsByKeys(anyList(), any())).thenReturn(List.of(listing(product, branch)));
        SearchResultCache cache = new SearchResultCache(
                com.autopartco.backend.shared.cache.ResilientCacheManager.local(),
                com.autopartco.backend.shared.cache.SharedCounters.local());
        service = new SearchService(jdbc.client, new QueryParser(dictionary), listingQueries, null, cache);
        SearchQuery query = SearchQuery.builder().q("pastillas").facets(false).build();

        SearchResult first = service.search(query, null);
        SearchResult second = service.search(query, null);

        // La parte orgánica salió de la caché (una sola consulta de página), el identificador no.
        assertThat(jdbc.callsMatching(sql -> sql.contains("limit :limit"))).hasSize(1);
        assertThat(first.searchId()).isNotNull();
        assertThat(second.searchId()).isNotNull().isNotEqualTo(first.searchId());
        assertThat(service.catalog(SearchQuery.builder().facets(false).build(), null).searchId()).isNull();
    }

    @Test
    void learnedCategoryOrdersBetweenYearAndCityWithoutFilteringOrAddingStatements() {
        UUID learned = UUID.randomUUID();
        QueryLearning learning = mock(QueryLearning.class);
        when(learning.categoryFor("pastillas")).thenReturn(java.util.Optional.of(learned));
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of());
        service.setQueryLearning(learning);

        service.search(SearchQuery.builder().q("pastillas").facets(false).build(), null);

        List<SearchFakeJdbc.Call> pages = jdbc.callsMatching(sql -> sql.contains("limit :limit"));
        assertThat(pages).isNotEmpty();
        String sql = pages.getFirst().sql();
        assertThat(sql).contains("year_ok desc, cat_ok desc, city_ok desc, text_rank desc")
                .contains("sl.category_path @> array[cast(:learnedCategoryId as uuid)]")
                .doesNotContain(learned.toString());
        assertThat(pages.getFirst().params()).containsEntry("learnedCategoryId", learned);
    }

    @Test
    void anExplicitCategoryOrNoLearningLeavesTheRelevanceUntouched() {
        QueryLearning learning = mock(QueryLearning.class);
        when(learning.categoryFor(any())).thenReturn(java.util.Optional.of(UUID.randomUUID()));
        jdbc.rows(sql -> sql.contains("limit :limit"), call -> List.of());
        service.setQueryLearning(learning);

        service.search(SearchQuery.builder().q("pastillas").categoryId(CATEGORY).facets(false).build(), null);
        verify(learning, never()).categoryFor(any());
        assertThat(jdbc.callsMatching(sql -> sql.contains("cat_ok"))).isEmpty();

        when(learning.categoryFor(any())).thenReturn(java.util.Optional.empty());
        service.search(SearchQuery.builder().q("pastillas").facets(false).build(), null);
        assertThat(jdbc.callsMatching(sql -> sql.contains("cat_ok"))).isEmpty();
    }

    private static ProductResponse listing(UUID product, UUID branch) {
        return new ProductResponse(product, UUID.randomUUID(), branch, "Sede", "Pastillas", "SKU", null, null,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, null, 1, null, null, false, CATEGORY, "ACTIVE",
                false, Map.of(), List.of(), List.of());
    }
}
