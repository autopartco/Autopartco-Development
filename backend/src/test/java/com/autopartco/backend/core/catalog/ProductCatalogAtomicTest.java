package com.autopartco.backend.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository.MatchRow;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImage;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductImageValidator;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.shared.storage.StorageException;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

/**
 * Alta y edición atómicas: requisitos de publicación sin efectos, publicación en la misma petición, fabricante nuevo,
 * galería y limpieza de Storage ante rollback. {@link ProductImages} y {@link StoreManufacturers} son reales; sus
 * repositorios y Storage son mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCatalogAtomicTest {
    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN9sAAAAASUVORK5CYII=";
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID BMW = UUID.randomUUID();

    @Mock private ProductRepository productRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StateRepository stateRepository;
    @Mock private BranchService branchService;
    @Mock private StockService stockService;
    @Mock private ListingQueries listingQueries;
    @Mock private ProductVehicleRules vehicleRules;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductImageMapper imageMapper;
    @Mock private SupabaseStorageService storageService;

    private ProductCatalog catalog;
    private final Manufacturer bosch = manufacturer("Bosch", true, null);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        ProductImages images = new ProductImages(productRepository, productImageRepository,
                new ProductImageValidator(), imageMapper, storageService);
        catalog = new ProductCatalog(productRepository, manufacturerRepository, categoryRepository, stateRepository,
                branchService, stockService, images, listingQueries, vehicleRules,
                new StoreManufacturers(manufacturerRepository));

        when(manufacturerRepository.findVisibleToStore(bosch.getId(), STORE)).thenReturn(Optional.of(bosch));
        when(categoryRepository.findById(any())).thenReturn(Optional.of(new Category()));
        when(stateRepository.findByCode(any())).thenAnswer(inv -> {
            State state = new State();
            state.setCode(inv.getArgument(0));
            return Optional.of(state);
        });
        when(vehicleRules.normalize(any())).thenAnswer(inv -> {
            List<VehicleRuleRequest> rules = inv.getArgument(0);
            return rules == null ? List.of() : rules;
        });
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            product.setId(PRODUCT);
            return product;
        });
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage image = inv.getArgument(0);
            image.setId(UUID.randomUUID());
            return image;
        });
        when(listingQueries.storeListing(any(), any(), any())).thenReturn(Optional.of(org.mockito.Mockito.mock(
                ProductResponse.class)));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    // --- alta ---

    @Test
    void publicarSinRequisitosEs409ConDetallesYNoCreaNiSubeNada() {
        ConflictException error = assertThrows(ConflictException.class, () -> catalog.create(STORE,
                create(bosch.getId(), null, null, List.of(), null, true), List.of(), false));

        assertEquals("PRODUCT_NOT_PUBLISHABLE", error.getCode());
        assertThat(codes(error)).containsExactly(
                PublicationIssue.NO_IMAGES, PublicationIssue.NO_VEHICLES, PublicationIssue.SUBSCRIPTION_INACTIVE);
        assertThat((List<?>) error.getDetails()).allSatisfy(
                issue -> assertFalse(((PublicationIssue) issue).message().isBlank()));
        assertNothingWritten();
    }

    @Test
    void publicarConImagenesPeroSinVehiculosNoSubeLasImagenes() {
        ConflictException error = assertThrows(ConflictException.class, () -> catalog.create(STORE,
                create(bosch.getId(), null, null, List.of(), null, true), List.of(png(), png()), true));

        assertThat(codes(error)).containsExactly(PublicationIssue.NO_VEHICLES);
        assertNothingWritten();
    }

    @Test
    void universalSinReglasSeCreaYPublicaSinVehiculos() {
        catalog.create(STORE, createUniversal(true, null, true), List.of(png()), true);

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertTrue(product.getValue().isUniversal());
        assertEquals(StateCodes.ACTIVE, product.getValue().getState().getCode());
        verify(vehicleRules).replace(PRODUCT, List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void universalConReglasEs400ValidationErrorEnVehicleRulesSinEscribir() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> catalog.create(STORE,
                createUniversal(true, List.of(new VehicleRuleRequest(BMW, null, null)), false), List.of(), true));

        assertEquals("VALIDATION_ERROR", error.getCode());
        assertThat((Map<String, String>) error.getDetails()).containsEntry("vehicleRules",
                "Un producto universal no lleva reglas de vehículo.");
        assertNothingWritten();
    }

    @Test
    void noUniversalSinReglasNoSePublicaNoVehicles() {
        ConflictException error = assertThrows(ConflictException.class, () -> catalog.create(STORE,
                createUniversal(false, null, true), List.of(png()), true));

        assertThat(codes(error)).containsExactly(PublicationIssue.NO_VEHICLES);
        assertNothingWritten();
    }

    @Test
    void pasarAUniversalEnEdicionBorraLasReglasExistentes() {
        Product product = existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(image(true, 0)));
        when(productImageRepository.countByProduct_Id(PRODUCT)).thenReturn(1L);

        catalog.update(STORE, BRANCH, PRODUCT, updateUniversal(true, null, null), List.of(), true);

        assertTrue(product.isUniversal());
        verify(vehicleRules).replace(PRODUCT, List.of());
    }

    @Test
    void pasarAUniversalConReglasEnEdicionEs400() {
        Product product = existingProduct();

        BadRequestException error = assertThrows(BadRequestException.class, () -> catalog.update(STORE, BRANCH,
                PRODUCT, updateUniversal(true, List.of(new VehicleRuleRequest(BMW, null, null)), null), List.of(),
                true));

        assertEquals("VALIDATION_ERROR", error.getCode());
        assertFalse(product.isUniversal());
        verify(vehicleRules, never()).replace(any(), any());
    }

    @Test
    void publicarUniversalExistenteNoExigeReglas() {
        Product product = existingProduct();
        product.setUniversal(true);
        when(productImageRepository.countByProduct_Id(PRODUCT)).thenReturn(1L);
        when(vehicleRules.forProduct(PRODUCT)).thenReturn(List.of());

        catalog.publish(STORE, PRODUCT, true);

        assertEquals(StateCodes.ACTIVE, product.getState().getCode());
    }

    @Test
    void publicarNoUniversalSinReglasEsNoVehicles() {
        existingProduct();
        when(productImageRepository.countByProduct_Id(PRODUCT)).thenReturn(1L);
        when(vehicleRules.forProduct(PRODUCT)).thenReturn(List.of());

        ConflictException error = assertThrows(ConflictException.class, () -> catalog.publish(STORE, PRODUCT, true));

        assertThat(codes(error)).containsExactly(PublicationIssue.NO_VEHICLES);
    }

    @Test
    void crearYPublicarEnLaMismaPeticionConFabricanteNuevoEImagenPrincipal() {
        when(manufacturerRepository.findByNormalizedName("Zeta Parts")).thenReturn(List.of());
        when(manufacturerRepository.countPendingByStore(STORE)).thenReturn(0L);
        when(manufacturerRepository.saveAndFlush(any(Manufacturer.class))).thenAnswer(inv -> inv.getArgument(0));
        List<VehicleRuleRequest> rules = List.of(new VehicleRuleRequest(BMW, null, null));

        catalog.create(STORE, create(null, "  Zeta   Parts ", true, rules, 1, true), List.of(png(), png()), true);

        ArgumentCaptor<Manufacturer> manufacturer = ArgumentCaptor.forClass(Manufacturer.class);
        verify(manufacturerRepository).saveAndFlush(manufacturer.capture());
        assertEquals("Zeta Parts", manufacturer.getValue().getName());
        assertFalse(manufacturer.getValue().isApproved());
        assertEquals(STORE, manufacturer.getValue().getSuggestedByStoreId());

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertEquals(StateCodes.ACTIVE, product.getValue().getState().getCode());
        assertEquals(manufacturer.getValue(), product.getValue().getManufacturer());
        verify(stockService).createListing(BRANCH, PRODUCT, 0, new BigDecimal("10.00"));
        verify(vehicleRules).replace(PRODUCT, rules);

        verify(storageService, times(2)).upload(anyString(), any(byte[].class), eq("image/png"), eq(false));
        ArgumentCaptor<ProductImage> images = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(images.capture());
        ProductImage second = images.getAllValues().get(1);
        verify(productImageRepository).clearMainImage(PRODUCT);
        verify(productImageRepository).markMain(PRODUCT, second.getId());
        assertTrue(second.isMain());
        assertEquals(1, second.getDisplayOrder());
        assertEquals(0, images.getAllValues().getFirst().getDisplayOrder());
    }

    @Test
    void stockCeroNoBloqueaLaPublicacion() {
        catalog.create(STORE, create(bosch.getId(), null, null, List.of(new VehicleRuleRequest(BMW, null, null)),
                null, true), List.of(png()), true);

        verify(stockService).createListing(BRANCH, PRODUCT, 0, new BigDecimal("10.00"));
    }

    @Test
    void fallaDeUnaImagenRevierteYLimpiaLasYaSubidas() {
        // Primera subida bien, segunda falla.
        doNothing().doThrow(new StorageException("caído"))
                .when(storageService).upload(anyString(), any(byte[].class), anyString(), eq(false));

        assertThrows(StorageException.class, () -> catalog.create(STORE,
                create(bosch.getId(), null, null, List.of(), null, false), List.of(png(), png()), true));

        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        verify(storageService, times(2)).upload(uploaded.capture(), any(byte[].class), anyString(), eq(false));
        rollBack();

        verify(storageService).delete(uploaded.getAllValues().get(0));
        verify(storageService).delete(uploaded.getAllValues().get(1));
    }

    @Test
    void fabricanteNuevoConParecidoSinConfirmarEs409YNoCreaNada() {
        MatchRow similar = org.mockito.Mockito.mock(MatchRow.class);
        when(similar.getId()).thenReturn(UUID.randomUUID());
        when(similar.getName()).thenReturn("Bosch");
        when(manufacturerRepository.findByNormalizedName("Bosh")).thenReturn(List.of());
        when(manufacturerRepository.findSimilar(eq(STORE), eq("Bosh"), anyDouble())).thenReturn(List.of(similar));

        ConflictException error = assertThrows(ConflictException.class, () -> catalog.create(STORE,
                create(null, "Bosh", null, List.of(), null, false), List.of(png()), true));

        assertEquals("MANUFACTURER_SIMILAR", error.getCode());
        assertThat((List<?>) error.getDetails()).singleElement()
                .isEqualTo(new StoreManufacturers.SimilarManufacturer(similar.getId(), "Bosch"));
        assertNothingWritten();
    }

    @Test
    void imagenPrincipalFueraDeRangoEs400() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> catalog.create(STORE,
                create(bosch.getId(), null, null, List.of(), 1, false), List.of(png()), true));

        assertEquals("INVALID_PRODUCT_IMAGE_MAIN", error.getCode());
        assertNothingWritten();
    }

    @Test
    void masDeDiezImagenesEs400SinSubir() {
        List<MultipartFile> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(i -> png()).toList();

        BadRequestException error = assertThrows(BadRequestException.class, () -> catalog.create(STORE,
                create(bosch.getId(), null, null, List.of(), null, false), eleven, true));

        assertEquals("PRODUCT_IMAGE_LIMIT_REACHED", error.getCode());
        assertNothingWritten();
    }

    // --- edición ---

    @Test
    void publicarQuitandoLaUnicaImagenEs409SinTocarStorage() {
        ProductImage only = image(true, 0);
        Product product = existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(only));
        when(vehicleRules.forProduct(PRODUCT)).thenReturn(List.of(
                new VehicleRuleResponse(BMW, "BMW", null, null, null, null)));

        ConflictException error = assertThrows(ConflictException.class, () -> catalog.update(STORE, BRANCH, PRODUCT,
                update(Set.of(only.getId()), null, null, true), List.of(), true));

        assertThat(codes(error)).containsExactly(PublicationIssue.NO_IMAGES);
        verifyNoInteractions(storageService);
        verify(productImageRepository, never()).deleteAll(anyList());
        assertEquals(StateCodes.INACTIVE, product.getState().getCode());
    }

    @Test
    void edicionReordenaConImagenNuevaYLaMarcaPrincipal() {
        ProductImage existing = image(true, 0);
        Product product = existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(existing));

        catalog.update(STORE, BRANCH, PRODUCT,
                update(null, List.of("new:0", existing.getId().toString()), "new:0", null), List.of(png()), true);

        ArgumentCaptor<ProductImage> created = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository).save(created.capture());
        assertEquals(0, created.getValue().getDisplayOrder());
        assertEquals(1, existing.getDisplayOrder());
        verify(productImageRepository).clearMainImage(PRODUCT);
        verify(productImageRepository).markMain(PRODUCT, created.getValue().getId());
        assertTrue(created.getValue().isMain());
        assertFalse(existing.isMain());
        assertEquals(product, created.getValue().getProduct());
    }

    @Test
    void ordenQueOmiteUnaImagenEs400SinSubir() {
        ProductImage existing = image(true, 0);
        existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(existing));

        BadRequestException error = assertThrows(BadRequestException.class, () -> catalog.update(STORE, BRANCH,
                PRODUCT, update(null, List.of("new:0"), null, null), List.of(png()), true));

        assertEquals("INVALID_PRODUCT_IMAGE_ORDER", error.getCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void quitarImagenRestauraElObjetoSiLaTransaccionSeRevierte() {
        ProductImage first = image(true, 0);
        ProductImage second = image(false, 1);
        existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(first, second));
        SupabaseStorageService.StoredObject backup = new SupabaseStorageService.StoredObject(new byte[] {1}, "image/png");
        when(storageService.download(first.getStoragePath())).thenReturn(backup);

        catalog.update(STORE, BRANCH, PRODUCT, update(Set.of(first.getId()), null, null, null), List.of(), true);

        verify(storageService).delete(first.getStoragePath());
        verify(productImageRepository).markMain(PRODUCT, second.getId());
        rollBack();
        verify(storageService).upload(first.getStoragePath(), backup.content(), "image/png", true);
    }

    @Test
    void editarProductoDeOtroAlmacenEs404SinTocarNada() {
        when(listingQueries.storeListing(STORE, BRANCH, PRODUCT)).thenReturn(Optional.empty());

        assertEquals("PRODUCT_NOT_FOUND", assertThrows(com.autopartco.backend.shared.error.NotFoundException.class,
                () -> catalog.update(STORE, BRANCH, PRODUCT, update(null, null, null, true), List.of(png()), true))
                .getCode());

        verify(productRepository, never()).lockOwned(any(), any());
        verifyNoInteractions(storageService);
    }

    @Test
    void quitarImagenDeOtroProductoEs404SinTocarStorage() {
        existingProduct();
        when(productImageRepository.findAllOfProduct(PRODUCT)).thenReturn(List.of(image(true, 0)));

        assertEquals("PRODUCT_IMAGE_NOT_FOUND", assertThrows(
                com.autopartco.backend.shared.error.NotFoundException.class,
                () -> catalog.update(STORE, BRANCH, PRODUCT, update(Set.of(UUID.randomUUID()), null, null, null),
                        List.of(), true)).getCode());

        verifyNoInteractions(storageService);
    }

    // --- utilidades ---

    private void assertNothingWritten() {
        verify(productRepository, never()).save(any());
        verify(manufacturerRepository, never()).saveAndFlush(any());
        verify(vehicleRules, never()).replace(any(), any());
        verifyNoInteractions(stockService, storageService);
        verify(productImageRepository, never()).save(any());
    }

    private static List<String> codes(ConflictException error) {
        return ((List<?>) error.getDetails()).stream().map(issue -> ((PublicationIssue) issue).code()).toList();
    }

    private static void rollBack() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private static ProductCreateRequest create(UUID manufacturerId, String newName, Boolean confirm,
            List<VehicleRuleRequest> rules, Integer mainIndex, boolean publish) {
        return new ProductCreateRequest(BRANCH, "Filtro", null, null, "desc", new BigDecimal("10.00"), 0,
                UUID.randomUUID(), manufacturerId, newName, confirm, rules, null, Map.of(), mainIndex, publish);
    }

    private ProductCreateRequest createUniversal(boolean universal, List<VehicleRuleRequest> rules,
            boolean publish) {
        return new ProductCreateRequest(BRANCH, "Aceite", null, null, "desc", new BigDecimal("10.00"), 0,
                UUID.randomUUID(), bosch.getId(), null, null, rules, universal, Map.of(), null, publish);
    }

    private static ProductUpdateRequest updateUniversal(Boolean universal, List<VehicleRuleRequest> rules,
            Boolean publish) {
        return new ProductUpdateRequest(null, null, null, null, null, null, null, null, null, null, rules, universal,
                null, publish, null, null, null);
    }

    private static ProductUpdateRequest update(Set<UUID> remove, List<String> order, String main, Boolean publish) {
        return new ProductUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null,
                publish, remove, order, main);
    }

    private Product existingProduct() {
        State inactive = new State();
        inactive.setCode(StateCodes.INACTIVE);
        Product product = Product.builder().storeId(STORE).manufacturer(bosch).state(inactive).build();
        product.setId(PRODUCT);
        when(productRepository.lockOwned(STORE, PRODUCT)).thenReturn(Optional.of(product));
        return product;
    }

    private static ProductImage image(boolean main, int order) {
        ProductImage image = ProductImage.builder()
                .storagePath("products/" + PRODUCT + "/" + UUID.randomUUID() + ".png")
                .contentType("image/png")
                .isMain(main)
                .displayOrder(order)
                .build();
        image.setId(UUID.randomUUID());
        return image;
    }

    private static Manufacturer manufacturer(String name, boolean approved, UUID store) {
        Manufacturer m = Manufacturer.builder().name(name).approved(approved).suggestedByStoreId(store).build();
        m.setId(UUID.randomUUID());
        return m;
    }

    private static MultipartFile png() {
        return new MockMultipartFile("images", "a.png", "image/png", Base64.getDecoder().decode(ONE_PIXEL_PNG));
    }
}
