package com.autopartco.backend.marketplace.stores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.organization.BusinessType;
import com.autopartco.backend.core.organization.PublicBranches;
import com.autopartco.backend.core.organization.PublishedStores;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.core.organization.StoreServices;
import com.autopartco.backend.core.pricing.ServicePricing;
import com.autopartco.backend.core.search.StoreSearchItem;
import com.autopartco.backend.core.search.StoreSearchQuery;
import com.autopartco.backend.core.search.StoreSearchResult;
import com.autopartco.backend.core.search.StoreSearchService;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

/** {@code GET /marketplace/stores/search}: contrato JSON, validación, límite por IP y ruta literal. */
class StoreSearchControllerTest {
    private static final String URL = "/api/v1/marketplace/stores/search";
    private static final UUID STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c33");

    private final StoreSearchService searchService = mock(StoreSearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(searchService.search(any())).thenAnswer(invocation -> {
            invocation.<StoreSearchQuery>getArgument(0).validate();
            return new StoreSearchResult(List.of(item()), 0, 12, 1);
        });
        mvc = MockMvcBuilders.standaloneSetup(new StoreSearchController(searchService, Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static StoreSearchItem item() {
        return new StoreSearchItem(STORE, "Taller Centro", "/api/v1/marketplace/stores/" + STORE + "/brand/logo?v=1",
                "Repuestos y servicios", List.of("Bogotá, D.C.", "Medellín"), 2,
                new StoreSearchItem.Rating(new BigDecimal("4.6"), 12), BusinessType.BOTH, 30, 4);
    }

    @Test
    void returnsItemsPageSizeAndTotalWithTheFullItemContract() throws Exception {
        mvc.perform(get(URL).param("q", "taller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(STORE.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Taller Centro"))
                .andExpect(jsonPath("$.items[0].logoUrl").value("/api/v1/marketplace/stores/" + STORE + "/brand/logo?v=1"))
                .andExpect(jsonPath("$.items[0].description").value("Repuestos y servicios"))
                .andExpect(jsonPath("$.items[0].cities[1]").value("Medellín"))
                .andExpect(jsonPath("$.items[0].branchCount").value(2))
                .andExpect(jsonPath("$.items[0].rating.average").value(4.6))
                .andExpect(jsonPath("$.items[0].rating.count").value(12))
                .andExpect(jsonPath("$.items[0].businessType").value("BOTH"))
                .andExpect(jsonPath("$.items[0].publishedProducts").value(30))
                .andExpect(jsonPath("$.items[0].activeServices").value(4));
    }

    @Test
    void passesTextCityPageAndSizeToTheService() throws Exception {
        mvc.perform(get(URL).param("q", "taller").param("cityCode", "11001").param("page", "2").param("size", "6"))
                .andExpect(status().isOk());

        ArgumentCaptor<StoreSearchQuery> query = ArgumentCaptor.forClass(StoreSearchQuery.class);
        verify(searchService).search(query.capture());
        assertThat(query.getValue()).isEqualTo(new StoreSearchQuery("taller", "11001", 2, 6));
    }

    @Test
    void noParametersListsWithDefaultsAndIsPublicWithoutAuthentication() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk());

        ArgumentCaptor<StoreSearchQuery> query = ArgumentCaptor.forClass(StoreSearchQuery.class);
        verify(searchService).search(query.capture());
        assertThat(query.getValue().q()).isNull();
        assertThat(query.getValue().pageOrDefault()).isZero();
        assertThat(query.getValue().sizeOrDefault()).isEqualTo(12);
    }

    @Test
    void invalidCityCodeIsRejectedBeforeSearching() throws Exception {
        for (String bad : List.of("1100", "110011", "abcde", "11 01")) {
            mvc.perform(get(URL).param("cityCode", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("SEARCH_PLACE_INVALID"));
        }
        verify(searchService, never()).search(any());
    }

    @Test
    void sizeMustBeBetween1And24AndPageNonNegative() throws Exception {
        mvc.perform(get(URL).param("size", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SIZE_INVALID"));
        mvc.perform(get(URL).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_SIZE_INVALID"));
        mvc.perform(get(URL).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SEARCH_PAGE_INVALID"));
        mvc.perform(get(URL).param("size", "24")).andExpect(status().isOk());
    }

    @Test
    void isRateLimitedTo120PerMinutePerIp() throws Exception {
        for (int i = 0; i < 120; i++) {
            mvc.perform(get(URL)).andExpect(status().isOk());
        }
        mvc.perform(get(URL))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("SEARCH_RATE_LIMITED"));
    }

    @Test
    void theLiteralSearchRouteDoesNotClashWithTheStoreProfileRoute() throws Exception {
        StoreBrowseController browse = new StoreBrowseController(mock(StoreService.class), mock(StoreFiles.class),
                mock(PublishedStores.class), mock(StoreReviewService.class), mock(ListingQueries.class),
                mock(SettingsQueries.class), mock(StoreServices.class), mock(PublicBranches.class),
                StorePublicCache.NONE, mock(ServicePricing.class));
        MockMvc both = MockMvcBuilders.standaloneSetup(browse,
                        new StoreSearchController(searchService, Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        both.perform(get(URL).param("q", "taller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Taller Centro"));
    }
}
