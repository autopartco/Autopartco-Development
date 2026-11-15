package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.search.Suggestions.StoreSuggestion;

class SuggestionsTest {
    private static final Predicate<String> STORES = sql -> sql.contains("select s.id, s.business_name, s.logo_path");

    @Test
    void popularQueriesNeedFiveSessionsOnTwoDistinctDays() {
        assertThat(Suggestions.MIN_QUERY_SESSIONS).isEqualTo(5);
        assertThat(Suggestions.MIN_QUERY_DAYS).isEqualTo(2);
        String sql = Suggestions.RELOAD_SQL.replaceAll("\s+", " ");
        assertThat(sql)
                .contains("having count(distinct ms.session_hash) >= 5")
                .contains("and count(distinct (ms.occurred_at at time zone 'America/Bogota')::date) >= 2")
                .doesNotContain(">= 3")
                .doesNotContain("%d");
    }

    private static Suggestions suggestions(SearchFakeJdbc jdbc) {
        SearchService search = mock(SearchService.class);
        when(search.search(any(), any())).thenReturn(new SearchResult(List.of(), 0, 4, 0, false, null, null));
        return new Suggestions(jdbc.client, search);
    }

    private static Map<String, Object> storeRow(UUID id, String name, String logo, String cities) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("business_name", name);
        row.put("logo_path", logo);
        row.put("cities", cities);
        return row;
    }

    @Test
    void storesAreSuggestedWithTheSameMatchLogoAndFirstCity() {
        UUID first = UUID.randomUUID();
        SearchFakeJdbc jdbc = new SearchFakeJdbc().rows(STORES, call -> List.of(
                storeRow(first, "Taller Centro", "stores/" + first + "/logo/v1.png", "Medellín|Bogotá"),
                storeRow(UUID.randomUUID(), "Taller Norte", null, null)));

        Suggestions.Result result = suggestions(jdbc).suggest("Tállér", null, null);

        assertThat(result.stores()).hasSize(2);
        StoreSuggestion store = result.stores().getFirst();
        assertThat(store.id()).isEqualTo(first);
        assertThat(store.name()).isEqualTo("Taller Centro");
        assertThat(store.logoUrl()).isEqualTo("/api/v1/marketplace/stores/" + first + "/brand/logo?v=v1");
        assertThat(store.city()).isEqualTo("Bogotá");
        assertThat(result.stores().get(1).city()).isNull();
        assertThat(result.stores().get(1).logoUrl()).isNull();

        SearchFakeJdbc.Call call = jdbc.callsMatching(STORES).getFirst();
        assertThat(call.params()).containsEntry("q", "taller").containsEntry("contains", "%taller%")
                .containsEntry("prefix", "taller%").containsEntry("cityCode", "")
                .containsEntry("limit", Suggestions.MAX_STORES);
        assertThat(Suggestions.MAX_STORES).isEqualTo(3);
        assertThat(call.sql().replaceAll("\s+", " "))
                .contains("similarity(n.nname, :q) >= 0.3")
                .contains("sa.status <> 'ACTIVE'")
                .contains("limit :limit");
    }

    @Test
    void aFailingStoreQueryNeverBreaksTheOtherSuggestions() {
        SearchFakeJdbc jdbc = new SearchFakeJdbc().rows(STORES, call -> {
            throw new IllegalStateException("db");
        });

        Suggestions.Result result = suggestions(jdbc).suggest("taller", null, null);

        assertThat(result.stores()).isEmpty();
        assertThat(result.services()).isEmpty();
    }
}
