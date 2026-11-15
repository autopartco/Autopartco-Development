package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.ServiceCategory;

/** Sin BD: SQL y parámetros de {@link ServiceSearchSql} (filtros, un resultado por servicio, orden, facetas). */
class ServiceSearchSqlTest {
    private static final UUID STORE = UUID.randomUUID();

    private static ServiceSearchSql.Criteria criteria() {
        return new ServiceSearchSql.Criteria(null, null, null, null, null, null, null, null, null, null, null, false,
                false, null, null, ServiceSearchQuery.Sort.RELEVANCE);
    }

    private static ServiceSearchSql.Criteria with(java.util.function.UnaryOperator<Builder> change) {
        return change.apply(new Builder()).build();
    }

    /** Constructor mínimo para variar un criterio sin repetir los 16 parámetros. */
    private static final class Builder {
        String tsQuery;
        String fuzzyText;
        ServiceCategory hintCategory;
        ServiceCategory category;
        String cityCode;
        String departmentCode;
        Double lat;
        Double lng;
        Integer radiusKm;
        UUID storeId;
        BigDecimal priceMax;
        boolean onSale;
        boolean bookable;
        java.util.Collection<UUID> openBranchIds;
        UUID exclude;
        ServiceSearchQuery.Sort sort = ServiceSearchQuery.Sort.RELEVANCE;

        Builder point(double latitude, double longitude) {
            lat = latitude;
            lng = longitude;
            return this;
        }

        ServiceSearchSql.Criteria build() {
            return new ServiceSearchSql.Criteria(tsQuery, fuzzyText, hintCategory, category, cityCode, departmentCode,
                    lat, lng, radiusKm, storeId, priceMax, onSale, bookable, openBranchIds, exclude, sort);
        }
    }

    @Test
    void withoutFiltersItSelectsOneRowPerServiceAndPaginates() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(criteria(), 24, 48);

        assertThat(statement.sql())
                .contains("from search_services ss", "select distinct on (service_id) *",
                        "order by service_id, no_location asc, service_key", "count(*) over () as total",
                        "limit :limit offset :offset", "order by score desc, service_id");
        assertThat(statement.params()).containsEntry("limit", 24).containsEntry("offset", 48L)
                .doesNotContainKeys("tsq", "category", "cityCode", "lat");
    }

    @Test
    void textSearchUsesTheSpanishUnaccentConfigAsAParameter() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(
                with(b -> {
                    b.tsQuery = "alineacion:*";
                    return b;
                }), 24, 0);

        assertThat(statement.sql())
                .contains("ss.document @@ to_tsquery('public.spanish_unaccent'::regconfig, :tsq)",
                        "ts_rank_cd(ss.document, to_tsquery('public.spanish_unaccent'::regconfig, :tsq))")
                .doesNotContain("alineacion");
        assertThat(statement.params()).containsEntry("tsq", "alineacion:*");
    }

    @Test
    void fuzzyTextUsesTrigramsInsteadOfTheTsQuery() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(
                with(b -> {
                    b.tsQuery = "alineacion:*";
                    b.fuzzyText = "alineacio";
                    return b;
                }), 24, 0);

        assertThat(statement.sql()).contains(":fuzzy <% ss.search_text", "word_similarity(:fuzzy, ss.search_text)")
                .doesNotContain(":tsq");
        assertThat(statement.params()).containsEntry("fuzzy", "alineacio").doesNotContainKey("tsq");
    }

    @Test
    void interpretedCategoryOnlyBoostsAndExplicitCategoryFilters() {
        ServiceSearchSql.Statement boosted = ServiceSearchSql.page(with(b -> {
            b.hintCategory = ServiceCategory.LUBRICATION;
            return b;
        }), 24, 0);
        assertThat(boosted.sql()).contains("(case when ss.category = :hintCategory then 2.0 else 1.0 end)")
                .doesNotContain("and ss.category = :category");
        assertThat(boosted.params()).containsEntry("hintCategory", "LUBRICATION").doesNotContainKey("category");

        ServiceSearchSql.Statement filtered = ServiceSearchSql.page(with(b -> {
            b.category = ServiceCategory.AC;
            return b;
        }), 24, 0);
        assertThat(filtered.sql()).contains("ss.category = :category");
        assertThat(filtered.params()).containsEntry("category", "AC");
    }

    @Test
    void placeStorePriceSaleAndBookingFiltersAreParameters() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(with(b -> {
            b.cityCode = "11001";
            b.departmentCode = "11";
            b.storeId = STORE;
            b.priceMax = new BigDecimal("80000");
            b.onSale = true;
            b.bookable = true;
            b.exclude = STORE;
            return b;
        }), 24, 0);

        assertThat(statement.sql()).contains("ss.city_code = :cityCode", "ss.department_code = :departmentCode",
                "ss.store_id = :storeId", "ss.price_from <= :priceMax", "ss.has_promotion", "ss.booking_enabled",
                "ss.service_id <> :excludeServiceId").doesNotContain("11001", "80000");
        assertThat(statement.params()).containsEntry("cityCode", "11001").containsEntry("departmentCode", "11")
                .containsEntry("storeId", STORE).containsEntry("priceMax", new BigDecimal("80000"))
                .containsEntry("excludeServiceId", STORE);
    }

    @Test
    void openNowFiltersByTheResolvedBranchesAndNoneOpenMatchesNothing() {
        UUID open = UUID.randomUUID();
        ServiceSearchSql.Statement some = ServiceSearchSql.page(with(b -> {
            b.openBranchIds = List.of(open);
            return b;
        }), 24, 0);
        assertThat(some.sql()).contains("ss.branch_id in (:openBranchIds)");
        assertThat(some.params()).containsEntry("openBranchIds", List.of(open));

        ServiceSearchSql.Statement none = ServiceSearchSql.page(with(b -> {
            b.openBranchIds = List.of();
            return b;
        }), 24, 0);
        assertThat(none.sql()).contains("      and false").doesNotContain(":openBranchIds");
    }

    @Test
    void nearestBranchIsChosenPerServiceWhenThereIsAPoint() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(with(b -> b.point(4.65, -74.08)), 24, 0);

        assertThat(statement.sql())
                .contains("asin(sqrt(", "order by service_id, distance_km asc nulls last, service_key",
                        "* (1.0 / (1.0 + coalesce(distance_km, 0) / 25.0))")
                .doesNotContain("4.65", "-74.08", "<= :radiusKm");
        assertThat(statement.params()).containsEntry("lat", 4.65).containsEntry("lng", -74.08);
    }

    @Test
    void radiusUsesACoordinateBoxThenHaversine() {
        ServiceSearchSql.Statement statement = ServiceSearchSql.page(with(b -> {
            b.point(4.65, -74.08);
            b.radiusKm = 10;
            return b;
        }), 24, 0);

        assertThat(statement.sql()).contains("ss.latitude between :south and :north",
                "ss.longitude between :west and :east", "<= :radiusKm");
        Map<String, Object> params = statement.params();
        assertThat((double) params.get("south")).isCloseTo(4.65 - 10 / 111.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat((double) params.get("west")).isLessThan(-74.08);
        assertThat(params).containsEntry("radiusKm", 10.0);
    }

    @Test
    void everySortHasItsOrderWithAStableTiebreaker() {
        assertThat(ServiceSearchSql.page(with(b -> {
            b.sort = ServiceSearchQuery.Sort.NEAREST;
            return b.point(4.65, -74.08);
        }), 24, 0).sql()).contains("order by distance_km asc nulls last, service_id\nlimit");
        assertThat(ServiceSearchSql.page(with(b -> {
            b.sort = ServiceSearchQuery.Sort.PRICE_ASC;
            return b;
        }), 24, 0).sql()).contains("order by price_from asc nulls last, service_id\nlimit");
        assertThat(ServiceSearchSql.page(with(b -> {
            b.sort = ServiceSearchQuery.Sort.RATING;
            return b;
        }), 24, 0).sql()).contains("order by store_rating desc nulls last, score desc, service_id\nlimit");
    }

    @Test
    void countAndFacetsOmitTheirOwnFilter() {
        ServiceSearchSql.Criteria both = with(b -> {
            b.category = ServiceCategory.TIRES;
            b.cityCode = "05001";
            b.bookable = true;
            return b;
        });

        ServiceSearchSql.Statement count = ServiceSearchSql.count(both);
        assertThat(count.sql()).contains("count(distinct ss.service_id)", "ss.category = :category",
                "ss.city_code = :cityCode");

        ServiceSearchSql.Statement categories = ServiceSearchSql.categoryFacets(both);
        assertThat(categories.sql()).contains("group by ss.category", "ss.city_code = :cityCode", "ss.booking_enabled")
                .doesNotContain("ss.category = :category");
        assertThat(categories.params()).doesNotContainKey("category");

        ServiceSearchSql.Statement cities = ServiceSearchSql.cityFacets(both);
        assertThat(cities.sql()).contains("left join geo_municipalities gm", "ss.city_code is not null",
                "ss.category = :category", "limit :cityLimit").doesNotContain("ss.city_code = :cityCode");
        assertThat(cities.params()).containsEntry("category", "TIRES").containsEntry("cityLimit", 20)
                .doesNotContainKey("cityCode");
    }

    @Test
    void withoutCityKeepsEveryOtherFilter() {
        ServiceSearchSql.Criteria base = with(b -> {
            b.tsQuery = "alineacion:*";
            b.cityCode = "11001";
            b.category = ServiceCategory.ALIGNMENT_BALANCING;
            return b;
        });

        ServiceSearchSql.Criteria relaxed = base.withoutCity();

        assertThat(relaxed.cityCode()).isNull();
        assertThat(relaxed.tsQuery()).isEqualTo("alineacion:*");
        assertThat(relaxed.category()).isEqualTo(ServiceCategory.ALIGNMENT_BALANCING);
    }
}
