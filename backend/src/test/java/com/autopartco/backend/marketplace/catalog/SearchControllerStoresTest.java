package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.analytics.SearchLog;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.BusinessType;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.pricing.PromotionExposureRecorder;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.core.search.StoreSearchItem;
import com.autopartco.backend.core.search.StoreSearchQuery;
import com.autopartco.backend.core.search.StoreSearchResult;
import com.autopartco.backend.core.search.StoreSearchService;
import com.autopartco.backend.core.search.Suggestions;
import com.autopartco.backend.marketplace.benefits.BuyerContexts;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

/** Franja {@code stores} de {@code /marketplace/search} y grupo {@code stores} de {@code /search/suggest}. */
class SearchControllerStoresTest {
    private static final String URL = "/api/v1/marketplace/search";
    private static final UUID PRODUCT = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");
    private static final UUID STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c33");

    private final SearchService searchService = mock(SearchService.class);
    private final Suggestions suggestions = mock(Suggestions.class);
    private final BuyerContexts buyerContexts = mock(BuyerContexts.class);
    private final StoreSearchService storeSearch = mock(StoreSearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(buyerContexts.forBrowsing(any())).thenReturn(BuyerContext.anonymous());
        ProductResponse listing = new ProductResponse(PRODUCT, UUID.randomUUID(), UUID.randomUUID(), "Centro",
                "Aceite 20W50", "SKU-1", "ACE-20", null, new BigDecimal("85000.00"), new BigDecimal("80000.00"),
                new BigDecimal("5000.00"), "Promo", 3, null, "Mobil", true, null, "ACTIVE", false, Map.of(), List.of(),
                List.of());
        when(searchService.search(any(), any(), any())).thenReturn(new SearchResult(
                List.of(new SearchResult.Hit(listing, null)), 0, 24, 1, false, null, null));
        when(storeSearch.search(any())).thenReturn(new StoreSearchResult(List.of(item("Taller Centro")), 0, 4, 9));
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService, suggestions,
                        mock(SearchLog.class), buyerContexts, mock(PromotionExposureRecorder.class),
                        mock(BranchService.class), null, storeSearch, Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static StoreSearchItem item(String name) {
        return new StoreSearchItem(STORE, name, null, null, List.of("Bogotá, D.C."), 1,
                new StoreSearchItem.Rating(null, 0), BusinessType.PARTS, 3, 0);
    }

    @Test
    void searchWithTextAddsTheStoreStripWithAtMostFourAndTheTotal() throws Exception {
        mvc.perform(get(URL).param("q", "taller").param("cityCode", "11001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.stores.total").value(9))
                .andExpect(jsonPath("$.stores.items.length()").value(1))
                .andExpect(jsonPath("$.stores.items[0].name").value("Taller Centro"))
                .andExpect(jsonPath("$.stores.items[0].cities[0]").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.stores.items[0].rating.count").value(0))
                .andExpect(jsonPath("$.stores.items[0].businessType").value("PARTS"));

        ArgumentCaptor<StoreSearchQuery> query = ArgumentCaptor.forClass(StoreSearchQuery.class);
        verify(storeSearch).search(query.capture());
        assertThat(query.getValue()).isEqualTo(new StoreSearchQuery("taller", "11001", 0, 4));
    }

    @Test
    void withoutTextThereIsNoStoresField() throws Exception {
        mvc.perform(get(URL).param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores").doesNotExist());
        verify(storeSearch, never()).search(any());
    }

    @Test
    void aFailingStoreSearchNeverBreaksTheProductSearch() throws Exception {
        when(storeSearch.search(any())).thenThrow(new IllegalStateException("db"));

        mvc.perform(get(URL).param("q", "aceite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.stores").doesNotExist());
    }

    @Test
    void suggestIncludesTheStoresGroupWithTheFirstCityOrNull() throws Exception {
        when(suggestions.suggest(eq("tall"), isNull(), isNull())).thenReturn(new Suggestions.Result(List.of(),
                List.of(), List.of(), List.of(), List.of(
                        new Suggestions.StoreSuggestion(STORE, "Taller Centro", "/logo", "Bogotá, D.C."),
                        new Suggestions.StoreSuggestion(UUID.randomUUID(), "Taller Norte", null, null))));

        mvc.perform(get(URL + "/suggest?q=tall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores[0].id").value(STORE.toString()))
                .andExpect(jsonPath("$.stores[0].name").value("Taller Centro"))
                .andExpect(jsonPath("$.stores[0].logoUrl").value("/logo"))
                .andExpect(jsonPath("$.stores[0].city").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.stores[1].city").isEmpty())
                .andExpect(jsonPath("$.services").isEmpty());
    }

    @Test
    void suggestResultNeverHasNullStoresAndKeepsTheOldConstructors() {
        assertThat(new Suggestions.Result(List.of(), List.of()).stores()).isEmpty();
        assertThat(new Suggestions.Result(List.of(), List.of(), null, null).stores()).isEmpty();
        assertThat(new Suggestions.Result(List.of(), List.of(), null, null, null).stores()).isEmpty();
        assertThat(Suggestions.Result.EMPTY.stores()).isEmpty();
    }
}
