package com.autopartco.backend.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private PromotionRepository promotionRepository;

    private ListingQueries queries;

    @BeforeEach
    void setUp() {
        queries = new ListingQueries(jdbcTemplate, productRepository, productImageRepository, productImageMapper,
                manufacturerRepository, categoryRepository, compatibilityService,
                new PriceCalculator(promotionRepository));
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
        assertThat(normalize(ListingQueries.PUBLISHED_LISTINGS_SQL)).endsWith(PUBLISHED_JOINS + PRODUCT_JOINS
                + "where i.deleted_at is null " + PUBLISHED_FILTER
                + "order by p.name, s.bussnies_name, b.name");
    }

    @Test
    void publishedListingKeepsTheFiltersAndOrderOfFindPublishedByProductId() {
        assertThat(normalize(ListingQueries.PUBLISHED_LISTING_SQL)).endsWith(PUBLISHED_JOINS + PRODUCT_JOINS
                + "where i.deleted_at is null and p.id = ? " + PUBLISHED_FILTER
                + "order by i.price, s.bussnies_name, b.name");
    }

    @Test
    void mapsRowsInSqlOrderWithCurrentCompatibilitiesAndBestPromotion() {
        UUID liveCompatibility = UUID.randomUUID();
        UUID deletedCompatibility = UUID.randomUUID();
        Product zeta = product("Zeta", Set.of(liveCompatibility, deletedCompatibility));
        Product alpha = product("Alpha", Set.of());
        UUID branchId = UUID.randomUUID();
        UUID branchStoreId = UUID.randomUUID();
        List<ListingRow> rows = List.of(
                new ListingRow(zeta.getId(), branchId, branchStoreId, "Centro", new BigDecimal("100.00"), 3),
                new ListingRow(alpha.getId(), branchId, branchStoreId, "Centro", new BigDecimal("40.00"), 1));
        when(jdbcTemplate.query(eq(ListingQueries.STORE_LISTINGS_SQL), any(RowMapper.class), eq(STORE_ID)))
                .thenReturn((List) rows);
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(alpha, zeta));
        when(compatibilityService.existingAmong(anyCollection())).thenReturn(Set.of(liveCompatibility));
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
        assertEquals(Set.of(liveCompatibility), first.compatibilityIds());
    }

    @Test
    void storeListingsWithoutRowsSkipTheOtherLookups() {
        when(jdbcTemplate.query(eq(ListingQueries.STORE_LISTINGS_SQL), any(RowMapper.class), eq(STORE_ID)))
                .thenReturn(List.of());

        assertThat(queries.storeListings(STORE_ID)).isEmpty();
        verifyNoInteractions(productRepository, productImageRepository, compatibilityService, promotionRepository);
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
        Product product = product("Filtro", Set.of());
        UUID branchId = UUID.randomUUID();
        when(jdbcTemplate.query(eq(ListingQueries.PUBLISHED_LISTING_SQL), any(RowMapper.class), eq(product.getId())))
                .thenReturn((List) List.of(new ListingRow(
                        product.getId(), branchId, STORE_ID, "Norte", new BigDecimal("80.00"), 2)));
        when(productRepository.findAllWithDetails(anyCollection())).thenReturn(List.of(product));
        when(compatibilityService.existingAmong(anyCollection())).thenReturn(Set.of());
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

    private Product product(String name, Set<UUID> compatibilityIds) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(UUID.randomUUID());
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
        product.setCompatibilityIds(new HashSet<>(compatibilityIds));
        return product;
    }

    private static String normalize(String sql) {
        return sql.strip().replaceAll("\\s+", " ");
    }
}
