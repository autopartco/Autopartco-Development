package com.autopartco.backend.marketplace.ads;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.pricing.BuyerContext;
import com.autopartco.backend.core.search.SearchResult;
import com.autopartco.backend.core.search.SearchService;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class MarketplaceAdsControllerTest {
    private static final String URL = "/api/v1/marketplace/ads";
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-4000-8000-000000000020");

    private final SearchService searchService = mock(SearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MarketplaceAdsController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static SearchResult.Sponsored ad(int n, String token) {
        UUID id = UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
        ProductResponse listing = new ProductResponse(id, UUID.randomUUID(), UUID.randomUUID(), "Centro",
                "Producto " + n, "SKU-" + n, null, null, new BigDecimal("85000.00"), new BigDecimal("80000.00"),
                new BigDecimal("5000.00"), null, 3, null, "Bosch", true, null, "ACTIVE", false, Map.of(), List.of(),
                List.of());
        return new SearchResult.Sponsored(new SearchResult.Hit(listing, null), token);
    }

    @Test
    void categoryTopReturnsSponsoredItemsWithTokensAndNoStore() throws Exception {
        when(searchService.categoryAds(eq(CATEGORY), eq("s:sess"), eq(BuyerContext.anonymous())))
                .thenReturn(List.of(ad(1, "tok-1"), ad(2, "tok-2")));

        mvc.perform(get(URL).param("placement", "CATEGORY_TOP").param("categoryId", CATEGORY.toString())
                        .header("X-Session-Id", "sess"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Producto 1"))
                .andExpect(jsonPath("$.items[0].sponsored").value(true))
                .andExpect(jsonPath("$.items[0].adImpressionToken").value("tok-1"))
                .andExpect(jsonPath("$.items[1].adImpressionToken").value("tok-2"))
                .andExpect(jsonPath("$.items[0].listing").doesNotExist());
    }

    @Test
    void productRelatedUsesTheProductAndFallsBackToTheIpAsVisitor() throws Exception {
        when(searchService.relatedAds(eq(PRODUCT), any(), any())).thenReturn(List.of(ad(3, "tok-3")));

        mvc.perform(get(URL).param("placement", "product_related").param("productId", PRODUCT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].adImpressionToken").value("tok-3"));

        verify(searchService).relatedAds(eq(PRODUCT), eq("ip:127.0.0.1"), eq(BuyerContext.anonymous()));
        verify(searchService, never()).categoryAds(any(), any(), any());
    }

    @Test
    void emptyResultIsAnEmptyList() throws Exception {
        when(searchService.categoryAds(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get(URL).param("placement", "CATEGORY_TOP").param("categoryId", CATEGORY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void invalidPlacementAndMissingTargetsAre400() throws Exception {
        for (String placement : new String[] {null, "", "SEARCH_TOP", "HOME_FEATURED", "NOPE"}) {
            MockHttpServletRequestBuilder request = get(URL).param("categoryId", CATEGORY.toString())
                    .param("productId", PRODUCT.toString());
            if (placement != null) {
                request = request.param("placement", placement);
            }
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ADS_PLACEMENT_INVALID"));
        }
        mvc.perform(get(URL).param("placement", "CATEGORY_TOP").param("productId", PRODUCT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADS_TARGET_REQUIRED"));
        mvc.perform(get(URL).param("placement", "PRODUCT_RELATED").param("categoryId", CATEGORY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADS_TARGET_REQUIRED"));
    }

    @Test
    void failureOfTheAdServerIsAnEmptyListNeverA500() throws Exception {
        when(searchService.relatedAds(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(get(URL).param("placement", "PRODUCT_RELATED").param("productId", PRODUCT.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void rateLimitsPerIp() throws Exception {
        when(searchService.categoryAds(any(), any(), any())).thenReturn(List.of());
        for (int i = 0; i < MarketplaceAdsController.ADS_PER_MINUTE; i++) {
            mvc.perform(get(URL).param("placement", "CATEGORY_TOP").param("categoryId", CATEGORY.toString()))
                    .andExpect(status().isOk());
        }
        mvc.perform(get(URL).param("placement", "CATEGORY_TOP").param("categoryId", CATEGORY.toString()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("ADS_RATE_LIMITED"));
    }
}
