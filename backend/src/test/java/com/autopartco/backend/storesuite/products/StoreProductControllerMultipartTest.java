package com.autopartco.backend.storesuite.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.ProductCreateRequest;
import com.autopartco.backend.core.catalog.ProductUpdateRequest;
import com.autopartco.backend.core.catalog.VehicleRuleRequest;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;

/** Contrato HTTP del alta/edición atómica: partes multipart, JSON de {@code data} y compatibilidad con JSON puro. */
class StoreProductControllerMultipartTest {
    private static final UUID STORE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BRANCH = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PRODUCT = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MAKE = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String BASE = "/api/v1/stores/" + STORE;
    private static final Authentication AUTH = new UsernamePasswordAuthenticationToken("staff@example.com", null);

    private final CreateProduct createProduct = mock(CreateProduct.class);
    private final UpdateProduct updateProduct = mock(UpdateProduct.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        StoreProductController controller = new StoreProductController(mock(ListStoreProducts.class), createProduct,
                updateProduct, mock(PublishProduct.class), mock(DeleteProduct.class), mock(GetStoreProduct.class),
                mock(AddProductListing.class), mock(RemoveProductListing.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static MockMultipartFile json(String name, String body) {
        return new MockMultipartFile(name, "", MediaType.APPLICATION_JSON_VALUE, body.getBytes());
    }

    private static MockMultipartFile image(String part, String name) {
        return new MockMultipartFile(part, name, "image/png", new byte[] {1, 2, 3});
    }

    private static String createJson(String manufacturer) {
        return """
                {"branchId":"%s","name":"Filtro","description":"Filtro de aceite","price":45000,"quantity":0,
                 "categoryId":"%s",%s,"vehicleRules":[{"makeId":"%s"}],"mainImageIndex":1,"publish":true}
                """.formatted(BRANCH, UUID.randomUUID(), manufacturer, MAKE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void altaMultipartPasaDataEImagenesEnOrden() throws Exception {
        mvc.perform(multipart(BASE + "/products")
                        .file(json("data", createJson("\"newManufacturerName\":\"Zeta\",\"confirmNewManufacturer\":true")))
                        .file(image("images", "uno.png"))
                        .file(image("images", "dos.png"))
                        .principal(AUTH))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProductCreateRequest> request = ArgumentCaptor.forClass(ProductCreateRequest.class);
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(createProduct).execute(eq(STORE), eq("staff@example.com"), request.capture(), files.capture());
        assertThat(request.getValue().vehicleRules()).containsExactly(new VehicleRuleRequest(MAKE, null, null));
        assertThat(request.getValue().publishRequested()).isTrue();
        assertThat(request.getValue().newManufacturerName()).isEqualTo("Zeta");
        assertThat(files.getValue()).extracting(MultipartFile::getOriginalFilename).containsExactly("uno.png", "dos.png");
    }

    @Test
    void altaSinParteDataEs400() throws Exception {
        mvc.perform(multipart(BASE + "/products").file(image("images", "uno.png")).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUEST_PART"));
        verifyNoInteractions(createProduct);
    }

    @Test
    void altaConFabricanteExistenteYNuevoALaVezEs400() throws Exception {
        String both = "\"manufacturerId\":\"" + UUID.randomUUID() + "\",\"newManufacturerName\":\"Zeta\"";
        mvc.perform(multipart(BASE + "/products").file(json("data", createJson(both))).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.manufacturerChoiceValid").exists());
        verifyNoInteractions(createProduct);
    }

    @Test
    void altaJsonPuroSigueFuncionando() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"manufacturerId\":\"" + UUID.randomUUID() + "\""))
                        .principal(AUTH))
                .andExpect(status().isCreated());
        verify(createProduct).execute(eq(STORE), eq("staff@example.com"), any(ProductCreateRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void edicionMultipartConNewImagesYOrden() throws Exception {
        UUID existing = UUID.randomUUID();
        String data = """
                {"vehicleRules":[],"publish":false,"removeImageIds":["%s"],"imageOrder":["new:0"],"mainImage":"new:0"}
                """.formatted(existing);

        mvc.perform(multipart(HttpMethod.PATCH, BASE + "/branches/" + BRANCH + "/products/" + PRODUCT)
                        .file(json("data", data))
                        .file(image("newImages", "nueva.png"))
                        .principal(AUTH))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductUpdateRequest> request = ArgumentCaptor.forClass(ProductUpdateRequest.class);
        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(updateProduct).execute(eq(STORE), eq(BRANCH), eq(PRODUCT), eq("staff@example.com"),
                request.capture(), files.capture());
        assertThat(request.getValue().vehicleRules()).isEmpty();
        assertThat(request.getValue().removeImageIds()).containsExactly(existing);
        assertThat(request.getValue().imageOrder()).containsExactly("new:0");
        assertThat(request.getValue().publish()).isFalse();
        assertThat(files.getValue()).hasSize(1);
    }

    @Test
    void edicionJsonPuroSigueFuncionando() throws Exception {
        when(updateProduct.execute(any(), any(), any(), any(), any())).thenReturn(null);

        mvc.perform(patch(BASE + "/branches/" + BRANCH + "/products/" + PRODUCT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo\"}")
                        .principal(AUTH))
                .andExpect(status().isOk());
        verify(updateProduct).execute(eq(STORE), eq(BRANCH), eq(PRODUCT), eq("staff@example.com"),
                any(ProductUpdateRequest.class));
        verify(updateProduct, org.mockito.Mockito.never()).execute(any(), any(), any(), any(), any(), anyList());
    }
}
