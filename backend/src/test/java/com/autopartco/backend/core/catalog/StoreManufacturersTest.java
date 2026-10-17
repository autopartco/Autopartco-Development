package com.autopartco.backend.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository.MatchRow;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.StateRepository;

@ExtendWith(MockitoExtension.class)
class StoreManufacturersTest {
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();

    @Mock private ManufacturerRepository repository;
    private StoreManufacturers service;

    @BeforeEach
    void setUp() {
        service = new StoreManufacturers(repository);
    }

    private static Manufacturer manufacturer(String name, boolean approved, UUID store) {
        Manufacturer m = Manufacturer.builder().name(name).approved(approved).suggestedByStoreId(store).build();
        m.setId(UUID.randomUUID());
        return m;
    }

    private static MatchRow row(String name) {
        MatchRow row = mock(MatchRow.class);
        when(row.getId()).thenReturn(UUID.randomUUID());
        when(row.getName()).thenReturn(name);
        return row;
    }

    @Test
    void exactoVisibleDevuelveElExistenteSinCrear() {
        Manufacturer bosch = manufacturer("Bosch", true, null);
        when(repository.findByNormalizedName("bosch")).thenReturn(List.of(bosch));

        StoreManufacturers.Suggestion result = service.suggest(STORE_A, new StoreManufacturerRequest(" bosch ", null));

        assertFalse(result.created());
        assertEquals(bosch.getId(), result.manufacturer().id());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void similarSinConfirmarDevuelve409ConSugerencias() {
        MatchRow similar = row("Bosch");
        when(repository.findByNormalizedName("Boch")).thenReturn(List.of());
        when(repository.findSimilar(eq(STORE_A), eq("Boch"), anyDouble())).thenReturn(List.of(similar));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.suggest(STORE_A, new StoreManufacturerRequest("Boch", false)));

        assertEquals("MANUFACTURER_SIMILAR", ex.getCode());
        assertEquals(List.of(new StoreManufacturers.SimilarManufacturer(similar.getId(), "Bosch")), ex.getDetails());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void similarConConfirmNewCreaPendienteDelAlmacen() {
        when(repository.findByNormalizedName("Boch")).thenReturn(List.of());
        when(repository.saveAndFlush(any(Manufacturer.class))).thenAnswer(inv -> {
            Manufacturer m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        StoreManufacturers.Suggestion result = service.suggest(STORE_A, new StoreManufacturerRequest("Boch", true));

        assertTrue(result.created());
        assertFalse(result.manufacturer().approved());
        ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(STORE_A, captor.getValue().getSuggestedByStoreId());
        assertFalse(captor.getValue().isApproved());
        verify(repository, never()).findSimilar(any(), any(), anyDouble());
    }

    @Test
    void sinSimilaresCreaPendiente() {
        when(repository.findByNormalizedName("Zeta")).thenReturn(List.of());
        when(repository.findSimilar(eq(STORE_A), eq("Zeta"), anyDouble())).thenReturn(List.of());
        when(repository.saveAndFlush(any(Manufacturer.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.suggest(STORE_A, new StoreManufacturerRequest("Zeta", null)).created());
    }

    @Test
    void pendienteDeOtroAlmacenNoSeRevelaYDa409() {
        Manufacturer ajeno = manufacturer("Zeta", false, STORE_B);
        when(repository.findByNormalizedName("zeta")).thenReturn(List.of(ajeno));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.suggest(STORE_A, new StoreManufacturerRequest("zeta", null)));

        assertEquals("MANUFACTURER_NAME_UNAVAILABLE", ex.getCode());
        assertEquals(null, ex.getDetails());
    }

    @Test
    void pendientePropioSeDevuelveSinCrear() {
        Manufacturer propio = manufacturer("Zeta", false, STORE_A);
        when(repository.findByNormalizedName("zeta")).thenReturn(List.of(propio));

        StoreManufacturers.Suggestion result = service.suggest(STORE_A, new StoreManufacturerRequest("zeta", null));

        assertFalse(result.created());
        assertEquals(propio.getId(), result.manufacturer().id());
    }

    @Test
    void carreraConIndiceUnicoDevuelveElGanador() {
        Manufacturer ganador = manufacturer("Zeta", false, STORE_A);
        when(repository.findByNormalizedName("Zeta")).thenReturn(List.of(), List.of(ganador));
        when(repository.findSimilar(eq(STORE_A), eq("Zeta"), anyDouble())).thenReturn(List.of());
        when(repository.saveAndFlush(any(Manufacturer.class))).thenThrow(new DataIntegrityViolationException("uq"));

        StoreManufacturers.Suggestion result = service.suggest(STORE_A, new StoreManufacturerRequest("Zeta", null));

        assertFalse(result.created());
        assertEquals(ganador.getId(), result.manufacturer().id());
    }

    @Test
    void busquedaCortaOVaciaNoConsultaYEscapaComodines() {
        assertTrue(service.search(STORE_A, null).isEmpty());
        assertTrue(service.search(STORE_A, " b ").isEmpty());
        verify(repository, never()).search(any(), any(), any());

        when(repository.search(eq(STORE_A), eq("a_%"), eq("a\\_\\%"))).thenReturn(List.of());
        assertTrue(service.search(STORE_A, "a_%").isEmpty());
    }

    @Test
    void listaSeAcotaAlAlmacen() {
        Manufacturer propio = manufacturer("Zeta", false, STORE_A);
        when(repository.findVisibleToStore(STORE_A)).thenReturn(List.of(propio));
        when(repository.findVisibleToStore(STORE_B)).thenReturn(List.of());

        assertEquals(1, service.list(STORE_A).size());
        assertTrue(service.list(STORE_B).isEmpty());
    }

    @Test
    void productCatalogRechazaFabricantePendienteAjeno() {
        ProductRepository productRepository = mock(ProductRepository.class);
        UUID manufacturerId = UUID.randomUUID();
        when(repository.findVisibleToStore(manufacturerId, STORE_A)).thenReturn(Optional.empty());
        ProductCatalog catalog = catalog(productRepository, mock(StateRepository.class), mock(ListingQueries.class));
        ProductCreateRequest request = new ProductCreateRequest(UUID.randomUUID(), "Filtro", null, null, "desc",
                new java.math.BigDecimal("10.00"), 1, UUID.randomUUID(), manufacturerId, null, null,
                List.of(), null, java.util.Map.of(), null, false);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> catalog.create(STORE_A, request, List.of(), true));

        assertEquals("MANUFACTURER_NOT_FOUND", ex.getCode());
        verify(productRepository, never()).save(any());
    }

    private ProductCatalog catalog(ProductRepository productRepository, StateRepository stateRepository,
            ListingQueries listingQueries) {
        return catalog(productRepository, stateRepository, listingQueries, mock(ProductImages.class),
                mock(ProductVehicleRules.class));
    }

    private ProductCatalog catalog(ProductRepository productRepository, StateRepository stateRepository,
            ListingQueries listingQueries, ProductImages images, ProductVehicleRules rules) {
        return new ProductCatalog(productRepository, repository, mock(CategoryRepository.class), stateRepository,
                mock(BranchService.class), mock(StockService.class), images, listingQueries, rules, service);
    }

    @Test
    void productCatalogRechazaFabricantePendienteAjenoAlActualizar() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ListingQueries listingQueries = mock(ListingQueries.class);
        UUID productId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID manufacturerId = UUID.randomUUID();
        when(listingQueries.storeListing(STORE_A, branchId, productId))
                .thenReturn(Optional.of(mock(ProductResponse.class)));
        when(productRepository.lockOwned(STORE_A, productId))
                .thenReturn(Optional.of(com.autopartco.backend.core.catalog.internal.Product.builder().build()));
        when(repository.findVisibleToStore(manufacturerId, STORE_A)).thenReturn(Optional.empty());
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null, null, null,
                manufacturerId, null, null, null, null, null, null, null, null, null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> catalog(productRepository, mock(StateRepository.class), listingQueries)
                        .update(STORE_A, branchId, productId, request, List.of(), true));

        assertEquals("MANUFACTURER_NOT_FOUND", ex.getCode());
    }

    @Test
    void publicarConFabricantePendientePropioEsValidoYRetirarTambien() {
        ProductRepository productRepository = mock(ProductRepository.class);
        StateRepository stateRepository = mock(StateRepository.class);
        ProductImages images = mock(ProductImages.class);
        ProductVehicleRules rules = mock(ProductVehicleRules.class);
        UUID productId = UUID.randomUUID();
        Manufacturer propio = manufacturer("Zeta", false, STORE_A);
        com.autopartco.backend.core.catalog.internal.Product product =
                com.autopartco.backend.core.catalog.internal.Product.builder().manufacturer(propio).build();
        when(productRepository.lockOwned(STORE_A, productId)).thenReturn(Optional.of(product));
        when(productRepository.findOwned(STORE_A, productId)).thenReturn(Optional.of(product));
        when(repository.findVisibleToStore(propio.getId(), STORE_A)).thenReturn(Optional.of(propio));
        when(images.count(productId)).thenReturn(1L);
        when(rules.forProduct(productId)).thenReturn(List.of(mock(VehicleRuleResponse.class)));
        when(stateRepository.findByCode(any())).thenReturn(Optional.of(new com.autopartco.backend.shared.persistence.State()));
        ProductCatalog catalog = catalog(productRepository, stateRepository, mock(ListingQueries.class), images, rules);

        catalog.publish(STORE_A, productId, true);
        verify(stateRepository).findByCode(StateCodes.ACTIVE);

        catalog.unpublish(STORE_A, productId);
        verify(stateRepository).findByCode(StateCodes.INACTIVE);
    }

    @Test
    void publicarConFabricantePendienteAjenoEsManufacturerInvalid() {
        ProductRepository productRepository = mock(ProductRepository.class);
        StateRepository stateRepository = mock(StateRepository.class);
        ProductImages images = mock(ProductImages.class);
        ProductVehicleRules rules = mock(ProductVehicleRules.class);
        UUID productId = UUID.randomUUID();
        Manufacturer ajeno = manufacturer("Zeta", false, STORE_B);
        when(productRepository.lockOwned(STORE_A, productId)).thenReturn(Optional.of(
                com.autopartco.backend.core.catalog.internal.Product.builder().manufacturer(ajeno).build()));
        when(repository.findVisibleToStore(ajeno.getId(), STORE_A)).thenReturn(Optional.empty());
        when(images.count(productId)).thenReturn(1L);
        when(rules.forProduct(productId)).thenReturn(List.of(mock(VehicleRuleResponse.class)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> catalog(productRepository, stateRepository, mock(ListingQueries.class), images, rules)
                        .publish(STORE_A, productId, true));

        assertEquals("PRODUCT_NOT_PUBLISHABLE", ex.getCode());
        assertEquals(List.of(PublicationIssue.MANUFACTURER_INVALID),
                ((List<?>) ex.getDetails()).stream().map(issue -> ((PublicationIssue) issue).code()).toList());
        verify(stateRepository, never()).findByCode(any());
    }

    @Test
    void planNewConParecidoSinConfirmarDa409SinEscribir() {
        MatchRow similar = row("Bosch");
        when(repository.findByNormalizedName("Bosh")).thenReturn(List.of());
        when(repository.findSimilar(eq(STORE_A), eq("Bosh"), anyDouble())).thenReturn(List.of(similar));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.planNew(STORE_A, "  Bosh ", null));

        assertEquals("MANUFACTURER_SIMILAR", ex.getCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void planNewExactoDevuelveExistenteYMaterializeNoCrea() {
        Manufacturer bosch = manufacturer("Bosch", true, null);
        when(repository.findByNormalizedName("bosch")).thenReturn(List.of(bosch));

        StoreManufacturers.NewManufacturer plan = service.planNew(STORE_A, "bosch", null);

        assertEquals(bosch, service.materialize(STORE_A, plan));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void materializeCreaPendientePropioYCarreraEsConflicto() {
        when(repository.findByNormalizedName("Zeta")).thenReturn(List.of());
        when(repository.countPendingByStore(STORE_A)).thenReturn(0L);
        StoreManufacturers.NewManufacturer plan = service.planNew(STORE_A, "Zeta", true);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Manufacturer created = service.materialize(STORE_A, plan);

        assertFalse(created.isApproved());
        assertEquals(STORE_A, created.getSuggestedByStoreId());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));
        assertEquals("MANUFACTURER_CONCURRENT",
                assertThrows(ConflictException.class, () -> service.materialize(STORE_A, plan)).getCode());
    }

    @Test
    void sugerirSuperadoElTopeDePendientesDa409SinCrear() {
        when(repository.findByNormalizedName("Zeta")).thenReturn(List.of());
        when(repository.findSimilar(eq(STORE_A), eq("Zeta"), anyDouble())).thenReturn(List.of());
        when(repository.countPendingByStore(STORE_A)).thenReturn((long) StoreManufacturers.MAX_PENDING_PER_STORE);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.suggest(STORE_A, new StoreManufacturerRequest("Zeta", null)));

        assertEquals("MANUFACTURER_PENDING_LIMIT", ex.getCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void existenteSeDevuelveAunqueSeAlcanzaraElTope() {
        Manufacturer propio = manufacturer("Zeta", false, STORE_A);
        when(repository.findByNormalizedName("Zeta")).thenReturn(List.of(propio));

        assertFalse(service.suggest(STORE_A, new StoreManufacturerRequest("Zeta", null)).created());
        verify(repository, never()).countPendingByStore(any());
    }

    @Test
    void busquedaSeTruncaA100Caracteres() {
        String largo = "a".repeat(150);
        String esperado = "a".repeat(100);
        when(repository.search(eq(STORE_A), eq(esperado), eq(esperado))).thenReturn(List.of());

        assertTrue(service.search(STORE_A, largo).isEmpty());
        verify(repository).search(STORE_A, esperado, esperado);
    }

    @Test
    void busquedaFijaUmbralesAntesDeConsultar() {
        when(repository.search(eq(STORE_A), eq("Bosh"), eq("Bosh"))).thenReturn(List.of());

        service.search(STORE_A, "Bosh");

        var order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).applySearchThresholds(StoreManufacturers.SEARCH_SIMILARITY,
                StoreManufacturers.SEARCH_WORD_SIMILARITY);
        order.verify(repository).search(STORE_A, "Bosh", "Bosh");
    }

    @Test
    void requestNormalizaEspaciosYRechazaCaracteresDeControl() {
        assertEquals("Bosch Mexico", new StoreManufacturerRequest("  Bosch     Mexico ", null).name());

        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new StoreManufacturerRequest("Bosch", null)).isEmpty());
            var violations = validator.validate(new StoreManufacturerRequest("Bo\u0000sch", null));
            assertEquals(1, violations.size());
            assertEquals("El nombre contiene caracteres no permitidos.", violations.iterator().next().getMessage());
            assertFalse(validator.validate(new StoreManufacturerRequest("Bo\nsch", null)).isEmpty());
        }
    }
}
