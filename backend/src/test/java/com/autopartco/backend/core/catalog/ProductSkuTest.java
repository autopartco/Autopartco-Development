package com.autopartco.backend.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.vehicle.CompatibilityService;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSkuTest {
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private ProductRepository productRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StateRepository stateRepository;
    @Mock private CompatibilityService compatibilityService;
    @Mock private BranchService branchService;
    @Mock private StockService stockService;
    @Mock private ProductImages productImages;
    @Mock private ListingQueries listingQueries;

    private ProductCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ProductCatalog(productRepository, manufacturerRepository, categoryRepository, stateRepository,
                compatibilityService, branchService, stockService, productImages, listingQueries);
        when(manufacturerRepository.findById(any())).thenReturn(Optional.of(mock(Manufacturer.class)));
        when(categoryRepository.findById(any())).thenReturn(Optional.of(mock(Category.class)));
        when(stateRepository.findByCode(any())).thenReturn(Optional.of(mock(State.class)));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(PRODUCT);
            return p;
        });
        when(listingQueries.storeListing(any(), any(), any())).thenReturn(Optional.of(mock(ProductResponse.class)));
    }

    private static ProductCreateRequest createRequest(String sku, BigDecimal price) {
        return new ProductCreateRequest(BRANCH, "Filtro", sku, null, "desc", price, 1, UUID.randomUUID(),
                UUID.randomUUID(), Set.of(UUID.randomUUID()), Map.of());
    }

    @Test
    void precioCreateRechazaCeroYAceptaUnCentavo() {
        assertFalse(VALIDATOR.validate(createRequest(null, BigDecimal.ZERO)).isEmpty());
        assertFalse(VALIDATOR.validate(createRequest(null, new BigDecimal("0.00"))).isEmpty());
        assertTrue(VALIDATOR.validate(createRequest(null, new BigDecimal("0.01"))).isEmpty());
    }

    @Test
    void precioUpdateRechazaCeroYAceptaUnCentavo() {
        assertFalse(VALIDATOR.validate(updateRequest(null, BigDecimal.ZERO)).isEmpty());
        assertFalse(VALIDATOR.validate(updateRequest(null, new BigDecimal("0.00"))).isEmpty());
        assertTrue(VALIDATOR.validate(updateRequest(null, new BigDecimal("0.01"))).isEmpty());
    }

    @Test
    void skuMuyLargoSeRechaza() {
        assertFalse(VALIDATOR.validate(createRequest("x".repeat(101), new BigDecimal("1.00"))).isEmpty());
    }

    @Test
    void skuVacioSeNormalizaANullEnCreate() {
        catalog.create(STORE_A, createRequest("   ", new BigDecimal("10.00")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertNull(captor.getValue().getSku());
        verify(productRepository, never()).skuInUse(any(), any(), any());
    }

    @Test
    void skuRepetidoEnElMismoAlmacenLanzaConflicto() {
        when(productRepository.skuInUse(STORE_A, "ABC-1", null)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> catalog.create(STORE_A, createRequest(" ABC-1 ", new BigDecimal("10.00"))));

        assertEquals("PRODUCT_SKU_ALREADY_EXISTS", ex.getCode());
        assertEquals("Ya existe un repuesto con ese SKU en el almacén.", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void mismoSkuEnOtroAlmacenEsValido() {
        when(productRepository.skuInUse(STORE_A, "ABC-1", null)).thenReturn(true);
        when(productRepository.skuInUse(STORE_B, "ABC-1", null)).thenReturn(false);

        catalog.create(STORE_B, createRequest("ABC-1", new BigDecimal("10.00")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("ABC-1", captor.getValue().getSku());
    }

    @Test
    void actualizarConSuPropioSkuNoChoca() {
        Product existing = Product.builder().storeId(STORE_A).sku("ABC-1").build();
        existing.setId(PRODUCT);
        when(productRepository.findOwned(STORE_A, PRODUCT)).thenReturn(Optional.of(existing));
        // La consulta excluye el propio producto, así que no hay otro con ese SKU.
        when(productRepository.skuInUse(STORE_A, "ABC-1", PRODUCT)).thenReturn(false);

        catalog.update(STORE_A, BRANCH, PRODUCT, updateRequest("ABC-1", null));

        verify(productRepository).skuInUse(eq(STORE_A), eq("ABC-1"), eq(PRODUCT));
        assertEquals("ABC-1", existing.getSku());
    }

    @Test
    void actualizarConSkuDeOtroProductoLanzaConflicto() {
        Product existing = Product.builder().storeId(STORE_A).sku("OLD").build();
        existing.setId(PRODUCT);
        when(productRepository.findOwned(STORE_A, PRODUCT)).thenReturn(Optional.of(existing));
        when(productRepository.skuInUse(STORE_A, "TAKEN", PRODUCT)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> catalog.update(STORE_A, BRANCH, PRODUCT, updateRequest("TAKEN", null)));
        assertEquals("OLD", existing.getSku());
    }

    @Test
    void actualizarConSkuVacioLoBorra() {
        Product existing = Product.builder().storeId(STORE_A).sku("OLD").build();
        existing.setId(PRODUCT);
        when(productRepository.findOwned(STORE_A, PRODUCT)).thenReturn(Optional.of(existing));

        catalog.update(STORE_A, BRANCH, PRODUCT, updateRequest("  ", null));

        assertNull(existing.getSku());
    }

    private static ProductUpdateRequest updateRequest(String sku, BigDecimal price) {
        return new ProductUpdateRequest(null, sku, null, null, price, null, null, null, null, null);
    }

    private static void assertFalse(boolean value) {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }
}
