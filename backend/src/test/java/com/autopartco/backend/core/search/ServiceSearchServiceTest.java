package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.shared.error.BadRequestException;

/**
 * Sin BD: flujo de {@link ServiceSearchService} con un {@code JdbcClient} falso. El SQL que filtra por visibilidad vive
 * en el índice ({@link ServiceSearchIndexerTest}); aquí se comprueban parámetros, respaldos, hidratación, un resultado
 * por servicio y «abierto ahora».
 */
class ServiceSearchServiceTest {
    private static final UUID SERVICE_A = UUID.randomUUID();
    private static final UUID SERVICE_B = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID BRANCH_NEAR = UUID.randomUUID();
    private static final UUID BRANCH_FAR = UUID.randomUUID();
    private static final BigDecimal LAT = new BigDecimal("4.65");
    private static final BigDecimal LNG = new BigDecimal("-74.08");
    /** Lunes 16-nov-2026 10:00 en Bogotá. */
    private static final Clock MONDAY_10AM = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneOffset.UTC);

    private static final Predicate<String> PAGE = sql -> sql.contains("with matched as");
    private static final Predicate<String> HYDRATE = sql -> sql.contains("join store_services s on s.id");

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final SearchDictionary dictionary = mock(SearchDictionary.class);
    private ServiceSearchService service;

    @BeforeEach
    void setUp() {
        when(dictionary.vocabulary()).thenReturn(QueryParserTest.vocabulary());
        service = new ServiceSearchService(jdbc.client, new QueryParser(dictionary), MONDAY_10AM);
    }

    private static Map<String, Object> pageRow(UUID serviceId, UUID branchId, Double distanceKm, long total) {
        Map<String, Object> row = new HashMap<>();
        row.put("service_key", serviceId + ":" + branchId);
        row.put("service_id", serviceId);
        row.put("branch_id", branchId);
        row.put("distance_km", distanceKm);
        row.put("total", total);
        return row;
    }

    private static Map<String, Object> hydrateRow(UUID serviceId, UUID branchId, String name, String category,
            String openingHours) {
        Map<String, Object> row = new HashMap<>();
        row.put("service_key", serviceId + ":" + branchId);
        row.put("service_id", serviceId);
        row.put("name", name);
        row.put("category", category);
        row.put("description", "Con equipo láser");
        row.put("image_url", null);
        row.put("duration_minutes", 45);
        row.put("price_from", new BigDecimal("60000.00"));
        row.put("base_price_from", new BigDecimal("60000.00"));
        row.put("has_promotion", false);
        row.put("booking_enabled", true);
        row.put("store_rating", new BigDecimal("4.6"));
        row.put("store_id", STORE);
        row.put("business_name", "Taller Centro");
        row.put("logo_path", "stores/" + STORE + "/logo/abc123.png");
        row.put("business_type", "BOTH");
        row.put("branch_id", branchId);
        row.put("branch_name", "Sede Chapinero");
        row.put("opening_hours", openingHours);
        row.put("city", "Bogotá, D.C.");
        row.put("address_line", "Calle 60 # 10-20");
        row.put("address_complement", "Local 2");
        row.put("latitude", new BigDecimal("4.6500000"));
        row.put("longitude", new BigDecimal("-74.0800000"));
        return row;
    }

    private SearchFakeJdbc.Call lastPageCall() {
        List<SearchFakeJdbc.Call> pages = jdbc.callsMatching(PAGE);
        return pages.getLast();
    }

    // --- Texto, ciudad y hidratación ---------------------------------------------------------------------------

    @Test
    void alignmentInBogotaSearchesTheTextFiltersTheCityAndBoostsTheCategory() {
        jdbc.rows(PAGE, call -> List.of(pageRow(SERVICE_A, BRANCH_NEAR, null, 1)));
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación y balanceo",
                "ALIGNMENT_BALANCING", null)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().q("alineacion bogota").build());

        SearchFakeJdbc.Call page = lastPageCall();
        assertThat(page.params()).containsEntry("tsq", "alineacion:*").containsEntry("cityCode", "11001")
                .containsEntry("hintCategory", "ALIGNMENT_BALANCING").containsEntry("limit", 24)
                .containsEntry("offset", 0L);
        assertThat(page.sql()).contains("ss.city_code = :cityCode").doesNotContain("bogota", "11001");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        ServiceHit hit = result.items().getFirst();
        assertThat(hit.id()).isEqualTo(SERVICE_A);
        assertThat(hit.category()).isEqualTo(ServiceCategory.ALIGNMENT_BALANCING);
        assertThat(hit.name()).isEqualTo("Alineación y balanceo");
        assertThat(hit.priceFrom()).isEqualByComparingTo("60000");
        assertThat(hit.basePriceFrom()).isEqualByComparingTo("60000");
        assertThat(hit.hasPromotion()).isFalse();
        assertThat(hit.durationMinutes()).isEqualTo(45);
        assertThat(hit.bookingEnabled()).isTrue();
        assertThat(hit.store().name()).isEqualTo("Taller Centro");
        assertThat(hit.store().businessType()).isEqualTo("BOTH");
        assertThat(hit.store().logoUrl()).isEqualTo("/api/v1/marketplace/stores/" + STORE + "/brand/logo?v=abc123");
        assertThat(hit.branch().city()).isEqualTo("Bogotá, D.C.");
        assertThat(hit.branch().address()).isEqualTo("Calle 60 # 10-20 Local 2");
        assertThat(hit.branch().openNow()).isNull();
        assertThat(hit.distanceKm()).isNull();
    }

    @Test
    void anExplicitCityOrDepartmentWinsOverTheInterpretedOne() {
        service.search(ServiceSearchQuery.builder().q("alineacion bogota").cityCode("05001").build());
        assertThat(lastPageCall().params()).containsEntry("cityCode", "05001");

        service.search(ServiceSearchQuery.builder().q("alineacion bogota").departmentCode("11").build());
        assertThat(lastPageCall().params()).doesNotContainKey("cityCode").containsEntry("departmentCode", "11");
    }

    @Test
    void ifTheInterpretedCityHasNoResultsItRepeatsWithoutIt() {
        jdbc.rows(PAGE, call -> call.params().containsKey("cityCode") ? List.of()
                : List.of(pageRow(SERVICE_B, BRANCH_FAR, null, 1)));
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_B, BRANCH_FAR, "Alineación", "ALIGNMENT_BALANCING",
                null)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().q("alineacion bogota").build());

        assertThat(jdbc.callsMatching(PAGE)).hasSize(2);
        assertThat(jdbc.callsMatching(PAGE).get(0).params()).containsEntry("cityCode", "11001");
        assertThat(jdbc.callsMatching(PAGE).get(1).params()).doesNotContainKey("cityCode");
        assertThat(result.items()).extracting(ServiceHit::id).containsExactly(SERVICE_B);
    }

    @Test
    void ifNoServiceHasAllTheWordsItFallsBackToTrigramsAfterSettingTheThreshold() {
        jdbc.rows(PAGE, call -> call.params().containsKey("fuzzy")
                ? List.of(pageRow(SERVICE_A, BRANCH_NEAR, null, 1)) : List.of());
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación", "ALIGNMENT_BALANCING",
                null)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().q("alinecion").build());

        assertThat(jdbc.callsMatching(sql -> sql.equals(ServiceSearchSql.FUZZY_THRESHOLD_SQL))).hasSize(1);
        assertThat(lastPageCall().params()).containsEntry("fuzzy", "alinecion").doesNotContainKey("tsq");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void withoutTextOrFiltersItListsEverythingAndSkipsTheHydrationWhenEmpty() {
        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().build());

        assertThat(lastPageCall().params()).doesNotContainKeys("tsq", "fuzzy", "cityCode", "hintCategory");
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(jdbc.callsMatching(HYDRATE)).isEmpty();
        assertThat(jdbc.callsMatching(sql -> sql.equals(ServiceSearchSql.FUZZY_THRESHOLD_SQL))).isEmpty();
    }

    // --- Un resultado por servicio y distancia -----------------------------------------------------------------

    @Test
    void returnsOneHitPerServiceWithTheNearestBranchAndTheDistanceInTheOrderOfThePage() {
        jdbc.rows(PAGE, call -> List.of(pageRow(SERVICE_B, BRANCH_NEAR, 2.34, 2), pageRow(SERVICE_A, BRANCH_FAR, 7.86, 2)));
        jdbc.rows(HYDRATE, call -> List.of(
                hydrateRow(SERVICE_A, BRANCH_FAR, "Alineación", "ALIGNMENT_BALANCING", null),
                hydrateRow(SERVICE_B, BRANCH_NEAR, "Balanceo", "ALIGNMENT_BALANCING", null)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().lat(LAT).lng(LNG)
                .sort(ServiceSearchQuery.Sort.NEAREST).build());

        assertThat(lastPageCall().sql()).contains("select distinct on (service_id) *",
                "order by service_id, distance_km asc nulls last, service_key");
        assertThat(lastPageCall().params()).containsEntry("lat", 4.65).containsEntry("lng", -74.08);
        assertThat(result.items()).extracting(ServiceHit::id).containsExactly(SERVICE_B, SERVICE_A);
        assertThat(result.items()).extracting(hit -> hit.branch().id()).containsExactly(BRANCH_NEAR, BRANCH_FAR);
        assertThat(result.items().get(0).distanceKm()).isEqualByComparingTo("2.3");
        assertThat(result.items().get(1).distanceKm()).isEqualByComparingTo("7.9");
        SearchFakeJdbc.Call hydrate = jdbc.callsMatching(HYDRATE).getFirst();
        assertThat(hydrate.params().get("keys")).isEqualTo(List.of(SERVICE_B + ":" + BRANCH_NEAR,
                SERVICE_A + ":" + BRANCH_FAR));
    }

    @Test
    void aKeyThatVanishedFromTheStoreBetweenQueriesIsOmittedNotFailed() {
        jdbc.rows(PAGE, call -> List.of(pageRow(SERVICE_A, BRANCH_NEAR, null, 2), pageRow(SERVICE_B, BRANCH_FAR, null, 2)));
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación", "ALIGNMENT_BALANCING",
                null)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().build());

        assertThat(result.items()).extracting(ServiceHit::id).containsExactly(SERVICE_A);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void anOutOfRangePageStillReportsTheRealTotal() {
        jdbc.scalar(sql -> sql.contains("select count(distinct ss.service_id)"), 7L);

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().page(5).size(24).build());

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(7);
        assertThat(lastPageCall().params()).containsEntry("offset", 120L);
    }

    // --- Filtros ----------------------------------------------------------------------------------------------

    @Test
    void passesCategoryPriceSaleBookingAndStoreFilters() {
        service.search(ServiceSearchQuery.builder().category(ServiceCategory.AC).priceMax(new BigDecimal("90000"))
                .onSale(true).bookable(true).storeId(STORE).sort(ServiceSearchQuery.Sort.PRICE_ASC).build());

        SearchFakeJdbc.Call page = lastPageCall();
        assertThat(page.params()).containsEntry("category", "AC").containsEntry("priceMax", new BigDecimal("90000"))
                .containsEntry("storeId", STORE);
        assertThat(page.sql()).contains("ss.has_promotion", "ss.booking_enabled",
                "order by price_from asc nulls last, service_id");
    }

    @Test
    void openNowKeepsOnlyBranchesOpenAtMondayTenInBogota() {
        UUID closedToday = UUID.randomUUID();
        UUID noSchedule = UUID.randomUUID();
        jdbc.rows(sql -> sql.equals(ServiceSearchService.OPEN_CANDIDATES_SQL), call -> List.of(
                Map.of("branch_id", BRANCH_NEAR, "opening_hours", "{\"mon\":[[\"08:00\",\"12:00\"]],\"sun\":[]}"),
                Map.of("branch_id", BRANCH_FAR, "opening_hours", "{\"mon\":[[\"13:00\",\"18:00\"]]}"),
                Map.of("branch_id", closedToday, "opening_hours", "{\"tue\":[[\"08:00\",\"18:00\"]]}"),
                Map.of("branch_id", noSchedule, "opening_hours", "{\"notes\":\"Llamar\"}")));

        service.search(ServiceSearchQuery.builder().openNow(true).build());

        assertThat(lastPageCall().sql()).contains("ss.branch_id in (:openBranchIds)");
        assertThat(lastPageCall().params().get("openBranchIds")).isEqualTo(List.of(BRANCH_NEAR));
    }

    @Test
    void openNowWithNoOpenBranchMatchesNothing() {
        service.search(ServiceSearchQuery.builder().openNow(true).build());

        assertThat(lastPageCall().sql()).contains("and false").doesNotContain(":openBranchIds");
    }

    @Test
    void theHitReportsOpenNowFromTheOpeningHoursOfItsBranch() {
        jdbc.rows(PAGE, call -> List.of(pageRow(SERVICE_A, BRANCH_NEAR, null, 1)));
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación", "ALIGNMENT_BALANCING",
                "{\"mon\":[[\"08:00\",\"12:00\"]]}")));

        assertThat(service.search(ServiceSearchQuery.builder().build()).items().getFirst().branch().openNow())
                .isTrue();

        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación", "ALIGNMENT_BALANCING",
                "{\"mon\":[[\"14:00\",\"18:00\"]]}")));
        assertThat(service.search(ServiceSearchQuery.builder().build()).items().getFirst().branch().openNow())
                .isFalse();
    }

    // --- Facetas ----------------------------------------------------------------------------------------------

    @Test
    void facetsListCategoriesAndCitiesWithCounts() {
        jdbc.rows(sql -> sql.contains("group by ss.category"), call -> List.of(
                Map.of("category", "ALIGNMENT_BALANCING", "n", 5L), Map.of("category", "AC", "n", 2L)));
        jdbc.rows(sql -> sql.contains("group by ss.city_code"), call -> List.of(
                Map.of("city_code", "11001", "city_name", "Bogotá, D.C.", "n", 6L)));

        ServiceSearchResult result = service.search(ServiceSearchQuery.builder().facets(true).build());

        assertThat(result.facets().categories()).containsExactly(
                new ServiceSearchResult.CategoryCount(ServiceCategory.ALIGNMENT_BALANCING, 5),
                new ServiceSearchResult.CategoryCount(ServiceCategory.AC, 2));
        assertThat(result.facets().cities()).containsExactly(new SearchResult.CityCount("11001", "Bogotá, D.C.", 6));
    }

    @Test
    void facetsDefaultToTheFirstPageOnly() {
        assertThat(service.search(ServiceSearchQuery.builder().build()).facets()).isNotNull();
        assertThat(service.search(ServiceSearchQuery.builder().page(1).build()).facets()).isNull();
        assertThat(service.search(ServiceSearchQuery.builder().facets(false).build()).facets()).isNull();
    }

    // --- Validaciones (mismos códigos que /marketplace/search) ----------------------------------------------------

    @Test
    void invalidParametersAre400WithTheSameCodesAsProductSearch() {
        assertCode(ServiceSearchQuery.builder().size(49).build(), "SEARCH_SIZE_INVALID");
        assertCode(ServiceSearchQuery.builder().size(0).build(), "SEARCH_SIZE_INVALID");
        assertCode(ServiceSearchQuery.builder().page(-1).build(), "SEARCH_PAGE_INVALID");
        assertCode(ServiceSearchQuery.builder().lat(LAT).build(), "SEARCH_POINT_INCOMPLETE");
        assertCode(ServiceSearchQuery.builder().lat(new BigDecimal("40.4")).lng(new BigDecimal("-3.7")).build(),
                "SEARCH_POINT_OUTSIDE_COVERAGE");
        assertCode(ServiceSearchQuery.builder().lat(LAT).lng(LNG).radiusKm(500).build(), "SEARCH_RADIUS_INVALID");
        assertCode(ServiceSearchQuery.builder().lat(LAT).lng(LNG).radiusKm(0).build(), "SEARCH_RADIUS_INVALID");
        assertCode(ServiceSearchQuery.builder().sort(ServiceSearchQuery.Sort.NEAREST).build(), "LOCATION_REQUIRED");
        assertCode(ServiceSearchQuery.builder().radiusKm(5).build(), "LOCATION_REQUIRED");
        assertCode(ServiceSearchQuery.builder().cityCode("110").build(), "SEARCH_PLACE_INVALID");
        assertCode(ServiceSearchQuery.builder().priceMax(new BigDecimal("-1")).build(), "SEARCH_PRICE_RANGE_INVALID");
        assertThatThrownBy(() -> ServiceSearchQuery.Sort.fromParameter("price_desc"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SEARCH_SORT_INVALID"));
        assertThat(ServiceSearchQuery.Sort.fromParameter("price_asc")).isEqualTo(ServiceSearchQuery.Sort.PRICE_ASC);
        assertThat(ServiceSearchQuery.Sort.fromParameter(null)).isEqualTo(ServiceSearchQuery.Sort.RELEVANCE);
        assertThat(jdbc.calls).isEmpty();
    }

    private void assertCode(ServiceSearchQuery query, String code) {
        assertThatThrownBy(() -> service.search(query)).isInstanceOfSatisfying(BadRequestException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    // --- Detalle, relacionados y categorías ----------------------------------------------------------------------

    @Test
    void findPicksTheNearestBranchWithAPointOrTheFirstWithLocationWithout() {
        jdbc.rows(sql -> sql.contains("where ss.service_id = :serviceId"),
                call -> List.of(pageRow(SERVICE_A, BRANCH_NEAR, 1.26, 1)));
        jdbc.rows(HYDRATE, call -> List.of(hydrateRow(SERVICE_A, BRANCH_NEAR, "Alineación", "ALIGNMENT_BALANCING",
                null)));

        ServiceHit withPoint = service.find(SERVICE_A, LAT, LNG).orElseThrow();
        SearchFakeJdbc.Call byPoint = jdbc.callsMatching(sql -> sql.contains("where ss.service_id = :serviceId"))
                .getFirst();
        assertThat(byPoint.sql()).contains("order by distance_km asc nulls last, ss.service_key");
        assertThat(byPoint.params()).containsEntry("serviceId", SERVICE_A).containsEntry("lat", 4.65);
        assertThat(withPoint.distanceKm()).isEqualByComparingTo("1.3");

        service.find(SERVICE_A, null, null);
        SearchFakeJdbc.Call byDefault = jdbc.callsMatching(sql -> sql.contains("where ss.service_id = :serviceId"))
                .getLast();
        assertThat(byDefault.sql()).contains("order by (ss.latitude is null) asc, ss.service_key");
        assertThat(byDefault.params()).doesNotContainKeys("lat", "lng");
    }

    @Test
    void findOfAServiceNotInTheIndexIsEmpty() {
        assertThat(service.find(UUID.randomUUID(), null, null)).isEmpty();
        assertThat(jdbc.callsMatching(HYDRATE)).isEmpty();
    }

    @Test
    void relatedAreOfTheSameCategoryExcludeTheServiceAndSortByDistanceFromItsBranch() {
        ServiceHit hit = new ServiceHit(SERVICE_A, "Alineación", ServiceCategory.ALIGNMENT_BALANCING, null, null,
                null, null, false, 45, true, new ServiceHit.Store(STORE, "Taller", null, null, "BOTH"),
                new ServiceHit.Branch(BRANCH_NEAR, "Sede", "Bogotá", "Calle 1", LAT, LNG, null), null);

        service.related(hit, null, null, 20);

        SearchFakeJdbc.Call page = lastPageCall();
        assertThat(page.params()).containsEntry("category", "ALIGNMENT_BALANCING")
                .containsEntry("excludeServiceId", SERVICE_A).containsEntry("lat", 4.65)
                .containsEntry("limit", ServiceSearchService.MAX_RELATED);
        assertThat(page.sql()).contains("order by distance_km asc nulls last, service_id");
        assertThat(jdbc.callsMatching(sql -> sql.contains("group by"))).isEmpty();
    }

    @Test
    void relatedWithoutAnyPointSortByRating() {
        ServiceHit hit = new ServiceHit(SERVICE_A, "Alineación", ServiceCategory.AC, null, null, null, null, false,
                null, true, new ServiceHit.Store(STORE, "Taller", null, null, "SERVICES"),
                new ServiceHit.Branch(BRANCH_NEAR, "Sede", null, null, null, null, null), null);

        service.related(hit, null, null, 3);

        assertThat(lastPageCall().sql()).contains("order by store_rating desc nulls last, score desc, service_id");
        assertThat(lastPageCall().params()).containsEntry("limit", 3).doesNotContainKey("lat");
    }

    @Test
    void categoriesCountOnlyNonEmptyOnesFromMostToLeastAndValidateTheCity() {
        jdbc.rows(sql -> sql.equals(ServiceSearchService.CATEGORY_COUNTS_SQL), call -> List.of(
                Map.of("category", "AC", "n", 2L), Map.of("category", "ALIGNMENT_BALANCING", "n", 9L),
                Map.of("category", "TIRES", "n", 0L), Map.of("category", "MECHANICS", "n", 2L),
                Map.of("category", "DESCONOCIDA", "n", 4L)));

        List<ServiceSearchResult.CategoryCount> counts = service.categories("11001");

        assertThat(counts).containsExactly(
                new ServiceSearchResult.CategoryCount(ServiceCategory.ALIGNMENT_BALANCING, 9),
                new ServiceSearchResult.CategoryCount(ServiceCategory.MECHANICS, 2),
                new ServiceSearchResult.CategoryCount(ServiceCategory.AC, 2));
        assertThat(jdbc.callsMatching(sql -> sql.equals(ServiceSearchService.CATEGORY_COUNTS_SQL)).getFirst().params())
                .containsEntry("cityCode", "11001");
        assertThatThrownBy(() -> service.categories("bogota")).isInstanceOfSatisfying(BadRequestException.class,
                error -> assertThat(error.getCode()).isEqualTo("SEARCH_PLACE_INVALID"));
    }
}
