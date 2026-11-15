package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.BusinessType;
import com.autopartco.backend.shared.error.BadRequestException;

/** Sin BD: parámetros, normalización, validación, mapeo y condiciones del SQL de {@link StoreSearchService}. */
class StoreSearchServiceTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final Predicate<String> PAGE = sql -> sql.contains("with matched as");

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final StoreSearchService service = new StoreSearchService(jdbc.client);

    private static Map<String, Object> row(long total) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", STORE);
        row.put("business_name", "Taller Centro");
        row.put("business_type", "BOTH");
        row.put("logo_path", "stores/" + STORE + "/logo/abc123.png");
        row.put("description", "  Repuestos\ny   servicios  ");
        row.put("cities", "Medellín|bogotá|Bogotá|Cali");
        row.put("branch_count", 4);
        row.put("average", new BigDecimal("4.55"));
        row.put("rating_count", 12L);
        row.put("total", total);
        row.put("published_products", 30L);
        row.put("active_services", 5L);
        return row;
    }

    private SearchFakeJdbc.Call lastCall() {
        return jdbc.callsMatching(PAGE).getLast();
    }

    @Test
    void mapsTheStoreWithBrandRatingCitiesAndCounts() {
        jdbc.rows(PAGE, call -> List.of(row(7)));

        StoreSearchResult result = service.search(new StoreSearchQuery("taller", null, null, null));

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(12);
        assertThat(result.total()).isEqualTo(7);
        StoreSearchItem item = result.items().getFirst();
        assertThat(item.id()).isEqualTo(STORE);
        assertThat(item.name()).isEqualTo("Taller Centro");
        assertThat(item.logoUrl()).isEqualTo("/api/v1/marketplace/stores/" + STORE + "/brand/logo?v=abc123");
        assertThat(item.description()).isEqualTo("Repuestos y servicios");
        assertThat(item.cities()).containsExactly("bogotá", "Bogotá", "Cali", "Medellín");
        assertThat(item.branchCount()).isEqualTo(4);
        assertThat(item.rating().average()).isEqualByComparingTo("4.6");
        assertThat(item.rating().count()).isEqualTo(12);
        assertThat(item.businessType()).isEqualTo(BusinessType.BOTH);
        assertThat(item.publishedProducts()).isEqualTo(30);
        assertThat(item.activeServices()).isEqualTo(5);
    }

    @Test
    void withoutReviewsTheAverageIsNullAndWithoutLogoOrDescriptionTheyAreNull() {
        Map<String, Object> bare = row(1);
        bare.put("average", null);
        bare.put("rating_count", 0L);
        bare.put("logo_path", null);
        bare.put("description", "   ");
        bare.put("cities", null);
        jdbc.rows(PAGE, call -> List.of(bare));

        StoreSearchItem item = service.search(new StoreSearchQuery(null, null, null, null)).items().getFirst();

        assertThat(item.rating().average()).isNull();
        assertThat(item.rating().count()).isZero();
        assertThat(item.logoUrl()).isNull();
        assertThat(item.description()).isNull();
        assertThat(item.cities()).isEmpty();
    }

    @Test
    void theDescriptionIsCutToAtMost160Characters() {
        Map<String, Object> long_ = row(1);
        long_.put("description", "a".repeat(400));
        jdbc.rows(PAGE, call -> List.of(long_));

        String description = service.search(new StoreSearchQuery("a", null, null, null)).items().getFirst()
                .description();

        assertThat(description).hasSize(StoreSearchItem.DESCRIPTION_MAX).endsWith("…");
        assertThat(StoreSearchItem.shorten("a".repeat(160))).hasSize(160).doesNotContain("…");
    }

    @Test
    void textIsNormalizedWithoutAccentsCaseOrLikeWildcards() {
        service.search(new StoreSearchQuery("  Taller ÁLFA_% ", null, null, null));

        assertThat(lastCall().params()).containsEntry("q", "taller alfa").containsEntry("contains", "%taller alfa%")
                .containsEntry("prefix", "taller alfa%").containsEntry("cityCode", "").containsEntry("limit", 12)
                .containsEntry("offset", 0L);
    }

    @Test
    void emptyTextListsEverythingAndThePageAndSizeSetTheOffset() {
        service.search(new StoreSearchQuery("   ", "11001", 2, 5));

        assertThat(lastCall().params()).containsEntry("q", "").containsEntry("cityCode", "11001")
                .containsEntry("limit", 5).containsEntry("offset", 10L);
    }

    @Test
    void anEmptyPageReportsZeroTotalAndKeepsPageAndSize() {
        StoreSearchResult result = service.search(new StoreSearchQuery("nada", null, 3, 6));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(6);
    }

    @Test
    void invalidInputIsRejectedWithoutQueryingTheDatabase() {
        for (String city : List.of("1100", "110011", "abcde", "11-01")) {
            assertThatThrownBy(() -> service.search(new StoreSearchQuery("x", city, null, null)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SEARCH_PLACE_INVALID"));
        }
        assertThatThrownBy(() -> service.search(new StoreSearchQuery("x", null, null, 25)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SEARCH_SIZE_INVALID"));
        assertThatThrownBy(() -> service.search(new StoreSearchQuery("x", null, null, 0)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SEARCH_SIZE_INVALID"));
        assertThatThrownBy(() -> service.search(new StoreSearchQuery("x", null, -1, null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SEARCH_PAGE_INVALID"));
        assertThat(jdbc.calls).isEmpty();
    }

    @Test
    void theSqlOnlyListsPublishedStoresAndMatchesNameDescriptionAndBranchCity() {
        service.search(new StoreSearchQuery("taller", "11001", null, null));
        String sql = lastCall().sql().replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("st.code = 'ACTIVE'")
                .contains("sa.status <> 'ACTIVE'")
                .contains("bs.code = 'ACTIVE'")
                .contains("public.immutable_unaccent(lower(s.business_name))")
                .contains("n.nname like :contains")
                .contains("similarity(n.nname, :q) >= 0.3")
                .contains("coalesce(s.configuration ->> 'description', '')")
                .contains("public.immutable_unaccent(lower(l.city)) like :contains")
                .contains("l.municipality_code = :cityCode")
                .contains("(:cityCode = '' or bi.in_city)")
                .contains("r.hidden_at is null")
                .contains("order by m.match_rank, m.sim desc, m.average desc nulls last")
                .contains("when n.nname = :q then 0 when n.nname like :prefix then 1")
                .doesNotContain("taller", "11001");
    }

    @Test
    void countsAreResolvedInSqlOnlyForThePage() {
        service.search(new StoreSearchQuery(null, null, null, null));
        String sql = lastCall().sql().replaceAll("\\s+", " ");

        assertThat(sql).contains("limit :limit offset :offset")
                .contains("count(distinct i.product_id) as products")
                .contains("i.stock > 0")
                .contains("from store_services ss")
                .contains("as published_products")
                .contains("as active_services");
        assertThat(sql.indexOf("limit :limit")).isLessThan(sql.indexOf("count(distinct i.product_id)"));
    }
}
