package com.autopartco.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;

class EtagFilterTest {
    private static final String M = "/api/v1/marketplace";

    @RestController
    static class FakeMarketplace {
        @GetMapping(M + "/stores/{id}")
        ResponseEntity<Map<String, String>> store(@PathVariable String id) {
            return "missing".equals(id) ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("id", id));
        }

        @GetMapping(M + "/search")
        ResponseEntity<Map<String, Object>> search(@RequestParam(defaultValue = "false") boolean ads) {
            Map<String, Object> body = Map.of("items", java.util.List.of("a"), "sponsored",
                    ads ? java.util.List.of("token") : java.util.List.of());
            return ads ? ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body) : ResponseEntity.ok(body);
        }

        @GetMapping(M + "/home")
        ResponseEntity<Map<String, String>> home() {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("k", "v"));
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FakeMarketplace())
                .addFilters(new MarketplaceHttpCacheFilter(true))
                .build();
    }

    @Test
    void publicGetHasWeakEtagAndPublicCacheControl() throws Exception {
        MvcResult result = mvc.perform(get(M + "/stores/s1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=30, stale-while-revalidate=120"))
                .andExpect(header().string("Vary", "Authorization"))
                .andReturn();
        assertThat(result.getResponse().getHeader("ETag")).startsWith("W/\"");
        assertThat(result.getResponse().getContentAsString()).contains("s1");
    }

    @Test
    void ifNoneMatchReturns304WithoutBody() throws Exception {
        String etag = mvc.perform(get(M + "/stores/s1")).andReturn().getResponse().getHeader("ETag");
        MvcResult result = mvc.perform(get(M + "/stores/s1").header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        // Comparación débil: el mismo valor sin W/ también coincide; otro valor no.
        mvc.perform(get(M + "/stores/s1").header("If-None-Match", etag.substring(2)))
                .andExpect(status().isNotModified());
        mvc.perform(get(M + "/stores/s1").header("If-None-Match", "W/\"otro\""))
                .andExpect(status().isOk());
    }

    @Test
    void withAuthorizationIsPrivateAndNeverPublic() throws Exception {
        MvcResult result = mvc.perform(get(M + "/stores/s1").header("Authorization", "Bearer x"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Vary", "Authorization, Cookie"))
                .andReturn();
        assertThat(result.getResponse().getHeaders("Cache-Control")).noneMatch(value -> value.contains("public"));
    }

    @Test
    void withSessionCookieIsPrivate() throws Exception {
        mvc.perform(get(M + "/stores/s1").cookie(new Cookie("autopartco_refresh", "r")))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Vary", "Authorization, Cookie"));
    }

    @Test
    void searchIsAlwaysPrivateWithEtag() throws Exception {
        String etag = mvc.perform(get(M + "/search"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Vary", "Authorization, Cookie"))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotNull();
        mvc.perform(get(M + "/search").header("If-None-Match", etag)).andExpect(status().isNotModified());
    }

    @Test
    void responsesWithAdsHaveNoEtagAndNever304() throws Exception {
        mvc.perform(get(M + "/search?ads=true").header("If-None-Match", "*"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(get(M + "/home"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void errorsAreNotTouched() throws Exception {
        mvc.perform(get(M + "/stores/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void routeTable() {
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/search/suggest"))
                .isEqualTo(MarketplaceHttpCacheFilter.Policy.PUBLIC);
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/products/abc"))
                .isEqualTo(MarketplaceHttpCacheFilter.Policy.PUBLIC);
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/product-options/categories"))
                .isEqualTo(MarketplaceHttpCacheFilter.Policy.PUBLIC);
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/stores/abc/branches"))
                .isEqualTo(MarketplaceHttpCacheFilter.Policy.PUBLIC);
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/home")).isNull();
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/ads")).isNull();
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/products")).isNull();
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/products/abc/frequently-bought")).isNull();
        assertThat(MarketplaceHttpCacheFilter.policyFor(M + "/stores/abc/brand/logo")).isNull();
    }
}
