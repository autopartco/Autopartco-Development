package com.autopartco.backend.marketplace.stores;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.organization.BranchMapQueries;
import com.autopartco.backend.core.organization.MapBranchView;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

class BranchMapControllerTest {
    private static final String URL = "/api/v1/marketplace/branches/map";
    private static final String BOX = "?south=4.5&west=-74.2&north=4.8&east=-74.0";
    private static final UUID ID = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");

    private final BranchMapQueries queries = mock(BranchMapQueries.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BranchMapController(queries))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MapBranchView view(BigDecimal distance) {
        return new MapBranchView(ID, "Repuestos", ID, "Centro", null, "Bogotá", "Cundinamarca",
                new BigDecimal("4.6097100"), new BigDecimal("-74.0817500"), distance);
    }

    @Test
    void withPointPassesItToTheQueryAndReturnsDistance() throws Exception {
        when(queries.find(any(), any(), any(), any(), eq(new BigDecimal("4.6")), eq(new BigDecimal("-74.1")), eq(4)))
                .thenReturn(List.of(view(new BigDecimal("1.6"))));

        mvc.perform(get(URL + BOX + "&lat=4.6&lng=-74.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(ID.toString()))
                .andExpect(jsonPath("$[0].distanceKm").value(1.6))
                .andExpect(jsonPath("$[0].area").doesNotExist())
                .andExpect(jsonPath("$[0].numberPhone").doesNotExist())
                .andExpect(jsonPath("$[0].addressLine").doesNotExist());
    }

    @Test
    void withoutPointDistanceIsNullAndLimitDefaultsToFour() throws Exception {
        when(queries.find(any(), any(), any(), any(), isNull(), isNull(), eq(4)))
                .thenReturn(List.of(view(null)));

        mvc.perform(get(URL + BOX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distanceKm").isEmpty());
    }

    @Test
    void boundsAreClippedToColombia() throws Exception {
        when(queries.find(any(), any(), any(), any(), any(), any(), eq(8))).thenReturn(List.of());

        mvc.perform(get(URL + "?south=-20&west=-100&north=30&east=-50&limit=8")).andExpect(status().isOk());

        verify(queries).find(new BigDecimal("-4.3"), new BigDecimal("-82"), new BigDecimal("13.5"),
                new BigDecimal("-66.8"), null, null, 8);
    }

    @Test
    void boundsOutsideColombiaAre400() throws Exception {
        mvc.perform(get(URL + "?south=40&west=-3&north=41&east=-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAP_AREA_OUTSIDE_COVERAGE"));
        verifyNoInteractions(queries);
    }

    @Test
    void missingBoundIs400() throws Exception {
        mvc.perform(get(URL + "?south=4.5&west=-74.2&north=4.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAP_BOUNDS_REQUIRED"));
        verifyNoInteractions(queries);
    }

    @Test
    void latWithoutLngIs400() throws Exception {
        mvc.perform(get(URL + BOX + "&lat=4.6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAP_POINT_INCOMPLETE"));
        verifyNoInteractions(queries);
    }

    @Test
    void sixtyFirstRequestFromSameIpIs429() throws Exception {
        when(queries.find(any(), any(), any(), any(), any(), any(), eq(4))).thenReturn(List.of());
        for (int i = 0; i < 60; i++) {
            mvc.perform(get(URL + BOX)).andExpect(status().isOk());
        }
        mvc.perform(get(URL + BOX))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("MAP_RATE_LIMITED"));
    }

    @Test
    void pointOutsideColombiaIs400() throws Exception {
        mvc.perform(get(URL + BOX + "&lat=1e400&lng=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAP_POINT_OUTSIDE_COVERAGE"));
        verifyNoInteractions(queries);
    }

    @Test
    void limitOutOfRangeIs400() throws Exception {
        mvc.perform(get(URL + BOX + "&limit=9")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAP_LIMIT_INVALID"));
        mvc.perform(get(URL + BOX + "&limit=0")).andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
    }
}
