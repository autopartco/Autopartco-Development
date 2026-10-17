package com.autopartco.backend.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.autopartco.backend.core.vehicle.VehicleCatalogService;
import com.autopartco.backend.core.vehicle.VehicleOption;
import com.autopartco.backend.core.vehicle.VersionOption;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;
import com.autopartco.backend.shared.error.NotFoundException;

class VehicleCatalogControllerTest {
    private static final String BASE = "/api/v1/marketplace/vehicles";
    private static final UUID ID = UUID.fromString("3f0c9a52-6f43-4a5e-9a51-2a1f7c0d9b10");

    private final VehicleCatalogService service = mock(VehicleCatalogService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new VehicleCatalogController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void makesReturnIdAndNameWithCacheHeaders() throws Exception {
        when(service.makes()).thenReturn(List.of(new VehicleOption(ID, "BMW")));

        mvc.perform(get(BASE + "/makes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].name").value("BMW"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void modelsReturnList() throws Exception {
        when(service.models(ID)).thenReturn(List.of(new VehicleOption(ID, "Serie 3")));

        mvc.perform(get(BASE + "/makes/" + ID + "/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Serie 3"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"));
    }

    @Test
    void versionsExposeLabel() throws Exception {
        when(service.versions(ID)).thenReturn(List.of(new VersionOption(ID, "320i")));

        mvc.perform(get(BASE + "/models/" + ID + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("320i"))
                .andExpect(jsonPath("$[0].name").doesNotExist());
    }

    @Test
    void unknownMakeIs404() throws Exception {
        when(service.models(ID)).thenThrow(new NotFoundException("MAKE_NOT_FOUND", "La marca no existe."));

        mvc.perform(get(BASE + "/makes/" + ID + "/models"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MAKE_NOT_FOUND"));
    }

    @Test
    void unknownModelIs404() throws Exception {
        when(service.versions(ID)).thenThrow(new NotFoundException("MODEL_NOT_FOUND", "El modelo no existe."));

        mvc.perform(get(BASE + "/models/" + ID + "/versions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MODEL_NOT_FOUND"));
    }

    @Test
    void matchingIfNoneMatchReturns304WithoutBody() throws Exception {
        when(service.makes()).thenReturn(List.of(new VehicleOption(ID, "BMW")));
        String etag = mvc.perform(get(BASE + "/makes")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        mvc.perform(get(BASE + "/makes").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=3600, public"));
    }

    @Test
    void changedContentChangesEtag() throws Exception {
        when(service.makes()).thenReturn(List.of(new VehicleOption(ID, "BMW")));
        String first = mvc.perform(get(BASE + "/makes")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        when(service.makes()).thenReturn(List.of(new VehicleOption(ID, "Audi")));

        mvc.perform(get(BASE + "/makes").header(HttpHeaders.IF_NONE_MATCH, first))
                .andExpect(status().isOk());
    }
}
