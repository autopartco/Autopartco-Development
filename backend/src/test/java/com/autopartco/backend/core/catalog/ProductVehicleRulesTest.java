package com.autopartco.backend.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.autopartco.backend.core.catalog.internal.ProductVehicleRule;
import com.autopartco.backend.core.catalog.internal.ProductVehicleRuleRepository;
import com.autopartco.backend.core.vehicle.VehicleCatalogService;
import com.autopartco.backend.core.vehicle.VehicleRefs;
import com.autopartco.backend.shared.error.BadRequestException;

/** Coherencia con el catálogo, normalización y reemplazo de reglas de vehículo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductVehicleRulesTest {
    private static final UUID BMW = UUID.randomUUID();
    private static final UUID FORD = UUID.randomUUID();
    private static final UUID SERIE_3 = UUID.randomUUID();
    private static final UUID MUSTANG = UUID.randomUUID();
    private static final UUID V_320I = UUID.randomUUID();
    private static final UUID V_GT = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    @Mock private ProductVehicleRuleRepository repository;
    @Mock private VehicleCatalogService vehicleCatalog;

    private ProductVehicleRules rules;

    @BeforeEach
    void setUp() {
        rules = new ProductVehicleRules(repository, vehicleCatalog);
        when(vehicleCatalog.resolve(anyCollection(), anyCollection(), anyCollection())).thenReturn(new VehicleRefs(
                Map.of(BMW, "BMW", FORD, "Ford"),
                Map.of(SERIE_3, new VehicleRefs.Ref(SERIE_3, BMW, "Serie 3"),
                        MUSTANG, new VehicleRefs.Ref(MUSTANG, FORD, "Mustang")),
                Map.of(V_320I, new VehicleRefs.Ref(V_320I, SERIE_3, "320i"),
                        V_GT, new VehicleRefs.Ref(V_GT, MUSTANG, "GT"))));
    }

    private static VehicleRuleRequest rule(UUID make, UUID model, UUID version) {
        return new VehicleRuleRequest(make, model, version);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bmwConMustangEs400VehicleRuleInvalidConDetallePorIndice() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> rules.normalize(List.of(
                rule(BMW, SERIE_3, null),
                rule(BMW, MUSTANG, null),
                rule(FORD, MUSTANG, V_320I))));

        assertEquals("VEHICLE_RULE_INVALID", error.getCode());
        assertThat((List<Object>) error.getDetails()).containsExactly(
                new ProductVehicleRules.RuleError(1, "modelId", "MODEL_NOT_IN_MAKE", "El modelo no pertenece a la marca."),
                new ProductVehicleRules.RuleError(2, "versionId", "VERSION_NOT_IN_MODEL",
                        "La versión no pertenece al modelo."));
    }

    @Test
    void versionSinModeloYMarcaInexistenteSonInvalidas() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> rules.normalize(List.of(
                rule(BMW, null, V_320I),
                rule(UUID.randomUUID(), null, null))));

        assertThat((List<?>) error.getDetails()).extracting("code")
                .containsExactly("VERSION_REQUIRES_MODEL", "MAKE_NOT_FOUND");
    }

    @Test
    void normalizaQuitandoDuplicadosYReglasContenidas() {
        List<VehicleRuleRequest> normalized = rules.normalize(List.of(
                rule(BMW, SERIE_3, V_320I),
                rule(FORD, MUSTANG, V_GT),
                rule(BMW, SERIE_3, null),
                rule(FORD, MUSTANG, V_GT),
                rule(BMW, null, null),
                rule(FORD, MUSTANG, null)));

        // «BMW, todos» absorbe Serie 3 y 320i; «Mustang, todas» absorbe GT y su duplicado.
        assertThat(normalized).containsExactly(rule(BMW, null, null), rule(FORD, MUSTANG, null));
    }

    @Test
    void listaVaciaONulaNoConsultaElCatalogo() {
        assertThat(rules.normalize(null)).isEmpty();
        assertThat(rules.normalize(List.of())).isEmpty();
        verifyNoInteractions(vehicleCatalog);
    }

    @Test
    void reemplazarBorraLasQueSobranCreaLasNuevasYConservaLasIguales() {
        ProductVehicleRule keep = ProductVehicleRule.builder().id(UUID.randomUUID()).productId(PRODUCT)
                .makeId(FORD).modelId(MUSTANG).build();
        ProductVehicleRule drop = ProductVehicleRule.builder().id(UUID.randomUUID()).productId(PRODUCT)
                .makeId(BMW).modelId(SERIE_3).versionId(V_320I).build();
        when(repository.findAllOfProduct(PRODUCT)).thenReturn(List.of(keep, drop));

        rules.replace(PRODUCT, List.of(rule(FORD, MUSTANG, null), rule(BMW, null, null)));

        verify(repository).softDelete(eq(PRODUCT), eq(List.of(drop.getId())), any());
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ProductVehicleRule>> saved = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(created -> {
            assertEquals(PRODUCT, created.getProductId());
            assertEquals(BMW, created.getMakeId());
            assertEquals(null, created.getModelId());
        });
    }

    @Test
    void reemplazarSinCambiosNoEscribe() {
        ProductVehicleRule keep = ProductVehicleRule.builder().id(UUID.randomUUID()).productId(PRODUCT)
                .makeId(BMW).build();
        when(repository.findAllOfProduct(PRODUCT)).thenReturn(List.of(keep));

        rules.replace(PRODUCT, List.of(rule(BMW, null, null)));

        verify(repository, never()).softDelete(any(), anyCollection(), any());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void lecturaConNombresOrdenadaYSinReglasDeCatalogoBorrado() {
        UUID other = UUID.randomUUID();
        when(repository.findAllOfProducts(anyCollection())).thenReturn(List.of(
                ProductVehicleRule.builder().productId(PRODUCT).makeId(BMW).modelId(SERIE_3).versionId(V_320I).build(),
                ProductVehicleRule.builder().productId(PRODUCT).makeId(BMW).build(),
                ProductVehicleRule.builder().productId(PRODUCT).makeId(other).build()));

        List<VehicleRuleResponse> result = rules.forProduct(PRODUCT);

        assertThat(result).containsExactly(
                new VehicleRuleResponse(BMW, "BMW", null, null, null, null),
                new VehicleRuleResponse(BMW, "BMW", SERIE_3, "Serie 3", V_320I, "320i"));
    }
}
