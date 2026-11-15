package com.autopartco.backend.marketplace.catalog;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.analytics.SearchClickLog;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class SearchClickControllerTest {
    private static final String URL = "/api/v1/marketplace/search/click";
    private static final UUID SEARCH = UUID.fromString("0b1f6d2a-93a4-4c6e-8f55-2d7a1c3e9b01");
    private static final UUID PRODUCT = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");

    private final SearchClickLog clickLog = mock(SearchClickLog.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SearchClickController(clickLog, Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String body(Object searchId, Object productId, Object position) {
        return "{\"searchId\":" + quote(searchId) + ",\"productId\":" + quote(productId) + ",\"position\":"
                + position + "}";
    }

    private static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void validClickIsAcceptedWithNoContentAndQueuedWithoutAuthentication() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(SEARCH, PRODUCT, 3)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(clickLog).record(SEARCH, PRODUCT, 3);
    }

    @Test
    void positionZeroIsValid() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(SEARCH, PRODUCT, 0)))
                .andExpect(status().isNoContent());
        verify(clickLog).record(SEARCH, PRODUCT, 0);
    }

    @Test
    void invalidBodyIsBadRequestAndNothingIsQueued() throws Exception {
        for (String invalid : new String[] {
                body(null, PRODUCT, 1),
                body(SEARCH, null, 1),
                body(SEARCH, PRODUCT, null),
                body(SEARCH, PRODUCT, -1),
                body("no-es-uuid", PRODUCT, 1),
                body(SEARCH, PRODUCT, "\"x\""),
                "{}",
                "no es json"}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verify(clickLog, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void passingTheRateLimitIsTooManyRequests() throws Exception {
        for (int i = 0; i < SearchClickController.CLICKS_PER_MINUTE; i++) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(SEARCH, PRODUCT, i)))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(SEARCH, PRODUCT, 1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("SEARCH_CLICK_RATE_LIMITED"));

        verify(clickLog, times(SearchClickController.CLICKS_PER_MINUTE)).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
