package com.autopartco.backend.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.autopartco.backend.core.catalog.ListingQueries.ListingRow;
import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.pricing.PriceCalculator;
import com.autopartco.backend.core.pricing.PromotionDiscountType;
import com.autopartco.backend.core.pricing.PromotionScope;
import com.autopartco.backend.core.pricing.PromotionTargetType;
import com.autopartco.backend.core.pricing.internal.Promotion;
import com.autopartco.backend.core.pricing.internal.PromotionRepository;
import com.autopartco.backend.core.vehicle.CompatibilityService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;

/**
 * Conversión JPQL → SQL nativo de los listados (fase 4a). El SQL esperado es el que Hibernate 7.4 generaba para el
 * JPQL original de {@code InventoryRepository} (capturado sin BD), con alias legibles y sin el {@code left join} de
 * compatibilidades ni el {@code distinct} (no cambian las filas devueltas).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class ListingQueriesTest {
    private static final String STORE_JOINS = "from inventory i "
            + "join branches b on b.id = i.branch_id and b.deleted_at is null ";
    private static final String PUBLISHED_JOINS = STORE_JOINS
            + "join store s on s.id = b.store_id and s.deleted_at is null "
            + "join states ss on ss.id = s.state and ss.deleted_at is null "
            + "join states bs on bs.id = b.state and bs.deleted_at is null ";
    private static final String PRODUCT_JOINS = "join products p on p.id = i.product_id and p.deleted_at is null "
            + "join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null "
            + "join categories c on c.id = p.category_id and c.deleted_at is null "
            + "join states ps on ps.id = p.state_id and ps.deleted_at is null ";
    /** Los joins públicos tampoco filtran por aprobación: un producto con fabricante pendiente propio se publica. */
    private static final String PUBLIC_PRODUCT_JOINS = PRODUCT_JOINS;
    private static final String PUBLISHED_FILTER = "and ps.code = 'ACTIVE' and ss.code = 'ACTIVE' "
            + "and bs.code = 'ACTIVE' and i.stock > 0 ";

    private static final UUID STORE_ID = UUID.randomUUID();

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private ManufacturerRepository manufacturerRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CompatibilityService compatibilityService;
    @Mock
    private ProductVehicleRules vehicleRules;
    @Mock
    private PromotionRepository promotionRepository;

    private ListingQueries queries;

    @BeforeEach
    void setUp() {
        queries = new ListingQueries(jdbcTemplate, productRepository, productImageRepository, productImageMapper,
                manufacturerRepository, categoryRepository, compatibilityService, vehicleRules,
                new PriceCalculator(promotionRepository));
    }

    @Test
    void consultasPublicasNoFiltranPorAprobacionDelFabricante() {
        org.junit.jupiter.api.Assertions.assertFalse(ListingQueries.PUBLISHED_LISTINGS_SQL.contains("approved"));
        org.junit.jupiter.api.Assertions.assertFalse(ListingQueries.PUBLISHED_LISTING_SQL.contains("approved"));
        org.junit.jupiter.api.Assertions.assertFalse(ListingQueries.PURCHASABLE_LISTINGS_SQL.contains("approved"));
    }

    @Test
    void storeListingsKeepTheFiltersAndOrderOfFindAllInStore() {
        assertThat(normalize(ListingQueries.STORE_LISTINGS_SQL)).endsWith(STORE_JOINS + PRODUCT_JOINS
                + "where i.deleted_at is null and p.store_id = ? order by p.name, b.name");
    }

    @Test
    void storeListingKeepsTheFiltersOfFindInStore() {
        assertThat(normalize(ListingQueries.STORE_LISTING_SQL)).endsWith(STORE_JOINS + PRODUCT_JOINS
                + "where i.deleted_at is null and p.id = ? and p.store_id = ? and b.id = ?");
    }

    @Test
    void publishedListingsKeepTheFiltersAndOrderOfFindPublished() {
        assertThat(normalize(ListingQueries.PUBLISHED_LISTINGS_SQL)).endsWith(PUBLISHED_JOINS + PUBLIC_PRODUCT_JOINS
                + "where i.deleted_at is null " + PUBLISHED_FILTER
                + "order by p.name, s.business_name, b.name");
    }

    @Test
    void publishedListingKeepsTheFiltersAndOrderOfFindPublishedByProductId() {
        assertThat(normalize(ListingQueries.PUBLISHED_LISTING_SQL)).endsWith(PUBLISHED_JOINS + PUBLIC_PRODUCT_JOINS
                + "where i.deleted_at is null and p.id = ? " + PUBLISHED_FILTER
                + "order by i.price, s.business_name, b.name");
    }

    @Test
    void mapsRowsInSqlOrderWithVehicleRulesManufacturerNameAndBestPromotion() {
        Product zeta = product("Zeta");
        zeta.getManufacturer().setName("Marca Pendiente");
        zeta.getManufacturer().setApproved(false);
        Product alpha = product("Alpha");
        VehicleRuleResponse bmwTodos = new VehicleRuleResponse(UUID.randomUUID(), "BMW", null, null, null, null);
        UUID branchId = UUID.randomUUID();
        UUID branchStoreId = UUID.randomUUID();
        List<ListingRow> rows = List.of(
                new ListingRow(zeta.getId(), branchId, branchStoreId, "Centro", new BigDecimal("100.00"), 3),
                new ListingRow(alpha.getId(), branchId, branchStoreId, "Centro", new BigDecimal("40.00"), 1));
        when(jdbcTemplate.query(eq(ListingQueries.STORE_LISTINGS_SQL), any(RowMapper.class), eq(STORE_ID)))
                .thenReturn((List) rows);
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(alpha, zeta));
        when(vehicleRules.forProducts(anyCollection())).thenReturn(Map.of(zeta.getId(), List.of(bmwTodos)));
        when(productImageRepository.findAllOfProducts(anyCollection()))
                .thenReturn(List.of());
        when(promotionRepository.findActiveAt(any())).thenReturn(List.of());

        List<ProductResponse> responses = queries.storeListings(STORE_ID);

        assertThat(responses).extracting(ProductResponse::name).containsExactly("Zeta", "Alpha");
        ProductResponse first = responses.getFirst();
        assertEquals(branchStoreId, first.storeId());
        assertEquals("Centro", first.branchName());
        assertEquals(new BigDecimal("100.00"), first.price());
        assertEquals(new BigDecimal("100.00"), first.effectivePrice());
        assertEquals(3, first.quantity());
        assertEquals("ACTIVE", first.state());
        assertEquals(List.of(bmwTodos), first.vehicleRules());
        // Marca visible en el marketplace aunque el fabricante esté pendiente.
        assertEquals("Marca Pendiente", first.manufacturerName());
        assertFalse(first.manufacturerApproved());
        assertEquals(List.of(), responses.get(1).vehicleRules());
        assertTrue(responses.get(1).manufacturerApproved());
    }

    @Test
    void publishedListingsByVehicleOnlyReadVehicleRulesWithAllWildcards() {
        UUID make = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), eq(make), eq(model), eq(version)))
                .thenReturn(List.of());

        assertThat(queries.publishedListings(new VehicleFilter(make, model, version))).isEmpty();

        String normalized = normalize(sql.getValue());
        assertThat(normalized)
                .contains(PUBLISHED_FILTER.strip())
                .contains("and (p.is_universal or exists (select 1 from product_vehicle_rules r where r.product_id = p.id "
                        + "and r.deleted_at is null and r.make_id = ? "
                        + "and (r.model_id is null or r.model_id = ?) "
                        + "and (r.version_id is null or r.version_id = ?) ))")
                .doesNotContain("product_compatibilities")
                .endsWith("order by p.name, s.business_name, b.name");
    }

    @Test
    void publishedListingsByMakeOnlyDoNotFilterModelOrVersion() {
        UUID make = UUID.randomUUID();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), eq(make))).thenReturn(List.of());

        queries.publishedListings(new VehicleFilter(make, null, null));

        assertThat(normalize(sql.getValue())).contains("r.make_id = ?").doesNotContain("r.model_id")
                .doesNotContain("r.version_id");
    }

    @Test
    void vehicleFilterRequiresParentLevels() {
        UUID id = UUID.randomUUID();
        assertEquals("VEHICLE_FILTER_INVALID", assertThrows(
                com.autopartco.backend.shared.error.BadRequestException.class,
                () -> new VehicleFilter(null, id, null)).getCode());
        assertThrows(com.autopartco.backend.shared.error.BadRequestException.class,
                () -> new VehicleFilter(id, null, id));
    }

    @Test
    void publishedListingsWithoutFilterKeepTheOriginalQuery() {
        when(jdbcTemplate.query(eq(ListingQueries.PUBLISHED_LISTINGS_SQL), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(queries.publishedListings(VehicleFilter.none())).isEmpty();
    }

    @Test
    void storeListingsWithoutRowsSkipTheOtherLookups() {
        when(jdbcTemplate.query(eq(ListingQueries.STORE_LISTINGS_SQL), any(RowMapper.class), eq(STORE_ID)))
                .thenReturn(List.of());

        assertThat(queries.storeListings(STORE_ID)).isEmpty();
        verifyNoInteractions(productRepository, productImageRepository, compatibilityService, vehicleRules,
                promotionRepository);
    }

    @Test
    void publishedListingWithoutRowsIsProductNotFound() {
        UUID productId = UUID.randomUUID();
        when(jdbcTemplate.query(eq(ListingQueries.PUBLISHED_LISTING_SQL), any(RowMapper.class), eq(productId)))
                .thenReturn(List.of());

        NotFoundException error = assertThrows(NotFoundException.class, () -> queries.publishedListing(productId));
        assertEquals("PRODUCT_NOT_FOUND", error.getCode());
    }

    @Test
    void appliesTheActivePromotionOfTheProductStore() {
        Product product = product("Filtro");
        UUID branchId = UUID.randomUUID();
        when(jdbcTemplate.query(eq(ListingQueries.PUBLISHED_LISTING_SQL), any(RowMapper.class), eq(product.getId())))
                .thenReturn((List) List.of(new ListingRow(
                        product.getId(), branchId, STORE_ID, "Norte", new BigDecimal("80.00"), 2)));
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(product));
        when(productImageRepository.findAllOfProducts(anyCollection()))
                .thenReturn(List.of());
        when(promotionRepository.findActiveAt(any())).thenReturn(List.of(
                Promotion.builder()
                        .scope(PromotionScope.STORE)
                        .storeId(STORE_ID)
                        .name("Semana")
                        .targetType(PromotionTargetType.PRODUCT)
                        .targetId(product.getId())
                        .discountType(PromotionDiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("25.00"))
                        .build()));

        ProductResponse response = queries.publishedListing(product.getId());

        assertEquals(new BigDecimal("60.00"), response.effectivePrice());
        assertEquals(new BigDecimal("20.00"), response.discountAmount());
        assertEquals("Semana", response.appliedPromotionName());
    }

    @Test
    void purchasableListingsWithoutKeysDoNotQuery() {
        assertThat(queries.purchasableListings(List.of())).isEmpty();
        verifyNoInteractions(jdbcTemplate, productRepository, productImageRepository, compatibilityService, vehicleRules,
                promotionRepository);
    }

    @Test
    void purchasableListingsKeepTheActiveFiltersWithoutTheStockFilter() {
        UUID productA = UUID.randomUUID();
        UUID branchA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), args.capture(), args.capture(),
                args.capture(), args.capture())).thenReturn(List.of());

        Map<ListingKey, ProductResponse> result = queries.purchasableListings(List.of(
                new ListingKey(productA, branchA), new ListingKey(productB, branchB)));

        assertThat(result).isEmpty();
        String normalized = normalize(sql.getValue());
        assertThat(normalized)
                .doesNotContain("stock > 0")
                .doesNotContain(ListingQueries.KEYS_PLACEHOLDER)
                .contains("ps.code = 'ACTIVE'")
                .contains("ss.code = 'ACTIVE'")
                .contains("bs.code = 'ACTIVE'")
                .contains("where i.deleted_at is null")
                .contains(PUBLISHED_JOINS.replace("from inventory i ", "") + PUBLIC_PRODUCT_JOINS)
                .contains("join (values (?::uuid, ?::uuid), (?::uuid, ?::uuid)) as k(product_id, branch_id) "
                        + "on k.product_id = i.product_id and k.branch_id = i.branch_id");
        assertThat(args.getAllValues()).containsExactly(productA, branchA, productB, branchB);
    }

    @Test
    void purchasableListingsApplyTheActivePromotionLikePublishedListing() {
        Product product = product("Filtro");
        UUID branchId = UUID.randomUUID();
        UUID missingProduct = UUID.randomUUID();
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(product.getId()), eq(branchId),
                eq(missingProduct), eq(branchId)))
                .thenReturn((List) List.of(new ListingRow(
                        product.getId(), branchId, STORE_ID, "Norte", new BigDecimal("80.00"), 0)));
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(product));
        when(productImageRepository.findAllOfProducts(anyCollection())).thenReturn(List.of());
        when(promotionRepository.findActiveAt(any())).thenReturn(List.of(
                Promotion.builder()
                        .scope(PromotionScope.STORE)
                        .storeId(STORE_ID)
                        .name("Semana")
                        .targetType(PromotionTargetType.PRODUCT)
                        .targetId(product.getId())
                        .discountType(PromotionDiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("25.00"))
                        .build()));

        Map<ListingKey, ProductResponse> result = queries.purchasableListings(List.of(
                new ListingKey(product.getId(), branchId), new ListingKey(missingProduct, branchId)));

        assertThat(result).containsOnlyKeys(new ListingKey(product.getId(), branchId));
        ProductResponse listing = result.get(new ListingKey(product.getId(), branchId));
        assertEquals(new BigDecimal("80.00"), listing.price());
        assertEquals(new BigDecimal("60.00"), listing.effectivePrice());
        assertEquals(new BigDecimal("20.00"), listing.discountAmount());
        assertEquals(0, listing.quantity());
    }

    @Test
    void omiteFilasDeProductosBorradosEnCarrera() {
        Product vivo = product("Vivo");
        UUID borrado = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(jdbcTemplate.query(eq(ListingQueries.STORE_LISTINGS_SQL), any(RowMapper.class), eq(STORE_ID)))
                .thenReturn((List) List.of(
                        new ListingRow(borrado, branchId, STORE_ID, "Centro", new BigDecimal("10.00"), 1),
                        new ListingRow(vivo.getId(), branchId, STORE_ID, "Centro", new BigDecimal("20.00"), 1)));
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(vivo));
        when(vehicleRules.forProducts(anyCollection())).thenReturn(Map.of());
        when(productImageRepository.findAllOfProducts(anyCollection())).thenReturn(List.of());
        when(promotionRepository.findActiveAt(any())).thenReturn(List.of());

        assertThat(queries.storeListings(STORE_ID)).extracting(ProductResponse::name).containsExactly("Vivo");
    }

    @Test
    void publishedListingDeProductoBorradoEnCarreraEsNotFound() {
        UUID productId = UUID.randomUUID();
        when(jdbcTemplate.query(eq(ListingQueries.PUBLISHED_LISTING_SQL), any(RowMapper.class), eq(productId)))
                .thenReturn((List) List.of(new ListingRow(
                        productId, UUID.randomUUID(), STORE_ID, "Norte", new BigDecimal("5.00"), 1)));
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of());
        when(vehicleRules.forProducts(anyCollection())).thenReturn(Map.of());
        when(productImageRepository.findAllOfProducts(anyCollection())).thenReturn(List.of());
        when(promotionRepository.findActiveAt(any())).thenReturn(List.of());

        assertEquals("PRODUCT_NOT_FOUND",
                assertThrows(NotFoundException.class, () -> queries.publishedListing(productId)).getCode());
    }

    private Product product(String name) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(UUID.randomUUID());
        manufacturer.setName("Bosch");
        manufacturer.setApproved(true);
        State state = new State();
        state.setCode("ACTIVE");
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setStoreId(STORE_ID);
        product.setName(name);
        product.setCategory(category);
        product.setManufacturer(manufacturer);
        product.setState(state);
        product.setAttributes(Map.of());
        return product;
    }

    private static String normalize(String sql) {
        return sql.strip().replaceAll("\\s+", " ");
    }
}
