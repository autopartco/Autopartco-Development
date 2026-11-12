package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;

/** Sin BD: SQL y parámetros del upsert, borrado de lo no publicado y lotes. */
class SearchIndexerTest {
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();

    private final SearchFakeJdbc jdbc = new SearchFakeJdbc();
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SearchIndexer indexer = new SearchIndexer(jdbc.client, listingQueries, transactions);

    @Test
    void upsertsPublishedListingsWithGeneralPriceAndRemovesTheOthersOfTheSameProducts() {
        when(listingQueries.publishedListingsOfProducts(List.of(PRODUCT))).thenReturn(List.of(
                listing(PRODUCT, BRANCH_A, "100000.00", "85000.00", "15000.00", 4),
                listing(PRODUCT, BRANCH_B, "90000.00", "90000.00", "0.00", 2)));

        int indexed = indexer.reindexProducts(List.of(PRODUCT));

        assertThat(indexed).isEqualTo(2);
        SearchFakeJdbc.Call upsert = jdbc.callsMatching(sql -> sql.startsWith("insert into search_listings")).getFirst();
        assertThat(upsert.sql())
                .contains("from (values (?::uuid, ?::uuid, ?::numeric, ?::int, ?::boolean), "
                        + "(?::uuid, ?::uuid, ?::numeric, ?::int, ?::boolean)) as k(")
                .doesNotContain(PRODUCT.toString());
        assertThat(upsert.positional()).containsExactly(
                PRODUCT, BRANCH_A, new BigDecimal("85000.00"), 4, true,
                PRODUCT, BRANCH_B, new BigDecimal("90000.00"), 2, false);
        SearchFakeJdbc.Call delete = jdbc.callsMatching(sql -> sql.startsWith("delete from search_listings")).getFirst();
        assertThat(delete.sql()).isEqualTo("delete from search_listings where product_id in (?) and listing_key not in (?,?)");
        assertThat(delete.positional()).containsExactly(PRODUCT, PRODUCT + ":" + BRANCH_A, PRODUCT + ":" + BRANCH_B);
        verify(transactions).commit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void productWithoutPublishedListingsIsRemovedFromTheIndex() {
        when(listingQueries.publishedListingsOfProducts(List.of(PRODUCT))).thenReturn(List.of());

        assertThat(indexer.reindexProducts(List.of(PRODUCT))).isZero();

        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert"))).isEmpty();
        SearchFakeJdbc.Call delete = jdbc.callsMatching(sql -> sql.startsWith("delete")).getFirst();
        assertThat(delete.sql()).isEqualTo("delete from search_listings where product_id in (?)");
        assertThat(delete.positional()).containsExactly(PRODUCT);
    }

    @Test
    void fullReindexGoesInBatchesAndDeletesWhatWasNotTouched() {
        List<UUID> ids = IntStream.range(0, 450).mapToObj(i -> UUID.randomUUID()).toList();
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-11-11T08:00:00Z");
        jdbc.scalar(sql -> sql.equals(SearchIndexer.NOW_SQL), startedAt);
        when(listingQueries.publishedProductIds()).thenReturn(ids);
        when(listingQueries.publishedListingsOfProducts(anyCollection())).thenAnswer(invocation -> {
            List<ProductResponse> listings = new ArrayList<>();
            for (Object id : invocation.<java.util.Collection<?>>getArgument(0)) {
                listings.add(listing((UUID) id, BRANCH_A, "10.00", "10.00", "0.00", 1));
            }
            return listings;
        });

        assertThat(indexer.reindexAll()).isEqualTo(450);

        verify(listingQueries, times(3)).publishedListingsOfProducts(anyCollection());
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("insert"))).hasSize(3);
        SearchFakeJdbc.Call untouched = jdbc.callsMatching(sql -> sql.equals(SearchIndexer.DELETE_UNTOUCHED_SQL))
                .getFirst();
        assertThat(untouched.positional()).containsExactly(startedAt);
        verify(transactions, times(4)).commit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void storeBranchAndInventoryScopesResolveTheirProducts() {
        UUID store = UUID.randomUUID();
        UUID inventory = UUID.randomUUID();
        jdbc.rows(sql -> sql.equals(SearchIndexer.STORE_PRODUCTS_SQL), call -> List.of(Map.of("id", PRODUCT)));
        jdbc.rows(sql -> sql.equals(SearchIndexer.BRANCH_PRODUCTS_SQL), call -> List.of(Map.of("product_id", PRODUCT)));
        jdbc.rows(sql -> sql.startsWith("select distinct product_id from inventory"),
                call -> List.of(Map.of("product_id", PRODUCT)));
        when(listingQueries.publishedListingsOfProducts(List.of(PRODUCT))).thenReturn(List.of());

        indexer.reindexStore(store);
        indexer.reindexBranch(BRANCH_A);
        indexer.reindexInventory(List.of(inventory));

        assertThat(jdbc.callsMatching(sql -> sql.equals(SearchIndexer.STORE_PRODUCTS_SQL)).getFirst().positional())
                .containsExactly(store, store);
        assertThat(jdbc.callsMatching(sql -> sql.equals(SearchIndexer.BRANCH_PRODUCTS_SQL)).getFirst().positional())
                .containsExactly(BRANCH_A, BRANCH_A);
        assertThat(jdbc.callsMatching(sql -> sql.startsWith("select distinct product_id from inventory where id in (?)"))
                .getFirst().positional()).containsExactly(inventory);
        verify(listingQueries, times(3)).publishedListingsOfProducts(List.of(PRODUCT));
    }

    @Test
    void upsertIndexesStoreAndProductRatingsWithoutDeletedOrHiddenReviews() {
        String sql = SearchIndexer.UPSERT_SQL.replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("store_rating, product_rating, product_review_count,",
                        "rt.rating, pr.rating, coalesce(pr.total, 0),",
                        "from store_reviews r where r.store_id = b.store_id and r.deleted_at is null"
                                + " and r.hidden_at is null",
                        "from product_reviews r where r.product_id = p.id and r.deleted_at is null"
                                + " and r.hidden_at is null",
                        "product_rating = excluded.product_rating",
                        "product_review_count = excluded.product_review_count");
    }

    @Test
    void upsertBuildsTheWeightedDocumentAndVehicleArraysInSql() {
        assertThat(SearchIndexer.UPSERT_SQL)
                .contains("on conflict (listing_key) do update set",
                        "k.product_id::text || ':' || k.branch_id::text",
                        "setweight(to_tsvector('public.spanish_unaccent'::regconfig,",
                        "concat_ws(' ', p.name, p.part_number, p.sku)), 'A')",
                        "concat_ws(' ', m.name, replace(c.code, '_', ' '))), 'B')",
                        "coalesce(v.names, '')), 'C')",
                        "concat_ws(' ', p.description, attr.text)), 'D')",
                        "public.immutable_unaccent(lower(",
                        "array_agg(distinct coalesce(r.model_id, r.make_id)) as model_ids",
                        "array_agg(distinct coalesce(r.version_id, r.model_id, r.make_id)) as version_ids",
                        "l.municipality_code, gm.department_code",
                        "round(avg(r.rating)::numeric, 1)",
                        "r.deleted_at is null");
    }

    private static ProductResponse listing(UUID product, UUID branch, String price, String effective,
            String discount, int stock) {
        return new ProductResponse(product, UUID.randomUUID(), branch, "Sede", "Pastillas", "SKU", null, null,
                new BigDecimal(price), new BigDecimal(effective), new BigDecimal(discount), null, stock, null, null,
                false, null, "ACTIVE", false, Map.of(), List.of(), List.of());
    }
}
