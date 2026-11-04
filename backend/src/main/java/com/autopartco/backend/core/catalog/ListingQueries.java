package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImageMapper;
import com.autopartco.backend.core.catalog.internal.ProductImageRepository;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.pricing.PriceCalculator;
import com.autopartco.backend.core.pricing.PriceQuote;
import com.autopartco.backend.core.pricing.PricingSubject;
import com.autopartco.backend.core.pricing.PromotionRule;
import com.autopartco.backend.core.vehicle.CompatibilityService;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Vistas de listado (producto en una sucursal) para el panel del almacén y el marketplace. Read model (§4.2 del
 * diseño): selecciona y ordena con SQL nativo sobre {@code inventory}, {@code branches}, {@code store},
 * {@code states} y {@code products}; los datos propios del producto se cargan con JPA y el precio efectivo lo
 * calcula {@code pricing}.
 *
 * <p>Las consultas reproducen exactamente el SQL que Hibernate generaba para el JPQL de
 * {@code InventoryRepository} ({@code findAllInStore}, {@code findPublished}, {@code findPublishedByProductId} y
 * {@code findInStore}): mismos {@code join} internos, con el {@code deleted_at is null} que Hibernate añadía por
 * {@code @SQLRestriction} a cada tabla unida, mismos filtros y mismo orden.</p>
 *
 * <p>Compatibilidad: solo {@code product_vehicle_rules} ({@link ProductVehicleRules}); la tabla
 * {@code product_compatibilities} ya no se lee ni se escribe. El filtro por vehículo del marketplace añade un
 * {@code exists} sobre las reglas vigentes ({@link #publishedListings(VehicleFilter)}).</p>
 */
@Service
@AllArgsConstructor
public class ListingQueries {

    static final String STORE_LISTINGS_SQL = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and p.store_id = ?
            order by p.name, b.name
            """;

    static final String STORE_LISTING_SQL = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and p.id = ?
              and p.store_id = ?
              and b.id = ?
            """;

    // Listados de un producto del almacén en cualquier sucursal (con su estado), por nombre de sucursal.
    static final String STORE_PRODUCT_LISTINGS_SQL = """
            select i.id as inventory_id, i.product_id, i.branch_id, b.store_id as branch_store_id,
                   b.name as branch_name, bs.code as branch_state, i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join states bs on bs.id = b.state and bs.deleted_at is null
            where i.deleted_at is null
              and i.product_id = ?
              and b.store_id = ?
            order by b.name
            """;

    static final String PUBLISHED_LISTINGS_WHERE_SQL = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null
            join states bs on bs.id = b.state and bs.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and ps.code = 'ACTIVE'
              and ss.code = 'ACTIVE'
              and bs.code = 'ACTIVE'
              and i.stock > 0
            """;

    static final String PUBLISHED_LISTINGS_ORDER_SQL = """
            order by p.name, s.business_name, b.name
            """;

    static final String PUBLISHED_LISTINGS_SQL = PUBLISHED_LISTINGS_WHERE_SQL + PUBLISHED_LISTINGS_ORDER_SQL;

    /**
     * Filtro por vehículo: el producto es universal o tiene alguna regla vigente que cubre el vehículo (misma marca y, por nivel,
     * regla «todos» ({@code null}) o el mismo id). Solo lee {@code product_vehicle_rules}.
     */
    static final String VEHICLE_MATCH_MAKE_SQL = """
              and (p.is_universal or exists (select 1 from product_vehicle_rules r
                          where r.product_id = p.id and r.deleted_at is null
                            and r.make_id = ?
            """;
    static final String VEHICLE_MATCH_MODEL_SQL = """
                            and (r.model_id is null or r.model_id = ?)
            """;
    static final String VEHICLE_MATCH_VERSION_SQL = """
                            and (r.version_id is null or r.version_id = ?)
            """;
    static final String VEHICLE_MATCH_END_SQL = """
                         ))
            """;

    static final String PUBLISHED_LISTING_SQL = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null
            join states bs on bs.id = b.state and bs.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and p.id = ?
              and ps.code = 'ACTIVE'
              and ss.code = 'ACTIVE'
              and bs.code = 'ACTIVE'
              and i.stock > 0
            order by i.price, s.business_name, b.name
            """;

    static final String KEYS_PLACEHOLDER = "{keys}";

    /**
     * Mismos joins y filtros de estado que {@link #PUBLISHED_LISTING_SQL} pero sin {@code i.stock > 0} (el stock lo
     * valida el descuento atómico de {@code inventory}). {@link #KEYS_PLACEHOLDER} se reemplaza por tantos
     * {@code (?::uuid, ?::uuid)} como claves; los valores siempre van como parámetros.
     */
    static final String PURCHASABLE_LISTINGS_SQL = """
            select i.id, i.product_id, i.branch_id, b.store_id as branch_store_id, b.name as branch_name,
                   i.price, i.stock
            from inventory i
            join (values {keys}) as k(product_id, branch_id)
              on k.product_id = i.product_id and k.branch_id = i.branch_id
            join branches b on b.id = i.branch_id and b.deleted_at is null
            join store s on s.id = b.store_id and s.deleted_at is null
            join states ss on ss.id = s.state and ss.deleted_at is null
            join states bs on bs.id = b.state and bs.deleted_at is null
            join products p on p.id = i.product_id and p.deleted_at is null
            join manufacturers m on m.id = p.manufacturer_id and m.deleted_at is null
            join categories c on c.id = p.category_id and c.deleted_at is null
            join states ps on ps.id = p.state_id and ps.deleted_at is null
            where i.deleted_at is null
              and ps.code = 'ACTIVE'
              and ss.code = 'ACTIVE'
              and bs.code = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageMapper productImageMapper;
    private final ManufacturerRepository manufacturerRepository;
    private final CategoryRepository categoryRepository;
    private final CompatibilityService compatibilityService;
    private final ProductVehicleRules vehicleRules;
    private final PriceCalculator priceCalculator;

    /** Todos los listados de los productos del almacén (cualquier estado), por nombre de producto y sucursal. */
    @Transactional(readOnly = true)
    public List<ProductResponse> storeListings(UUID storeId) {
        return toResponses(jdbcTemplate.query(STORE_LISTINGS_SQL, ListingQueries::mapRow, storeId));
    }

    /**
     * Listado de un producto del almacén en una sucursal, sin filtrar por estado. Si se llama tras escribir en la
     * misma transacción, el llamador debe hacer {@code flush} antes (es SQL nativo).
     */
    @Transactional(readOnly = true)
    public Optional<ProductResponse> storeListing(UUID storeId, UUID branchId, UUID productId) {
        ListingRow row = DataAccessUtils.singleResult(
                jdbcTemplate.query(STORE_LISTING_SQL, ListingQueries::mapRow, productId, storeId, branchId));
        return row == null ? Optional.empty() : toResponses(List.of(row)).stream().findFirst();
    }

    /**
     * Detalle de un producto del almacén con todos sus listados por sucursal. Si el producto no es del almacén,
     * {@code PRODUCT_NOT_FOUND}. Es SQL nativo: tras escribir en la misma transacción, el llamador debe hacer
     * {@code flush} antes.
     */
    @Transactional(readOnly = true)
    public StoreProductDetail storeProductDetail(UUID storeId, UUID productId) {
        productRepository.findOwned(storeId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
        Product product = productRepository.findAllWithDetails(Set.of(productId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
        List<DetailRow> rows = jdbcTemplate.query(
                STORE_PRODUCT_LISTINGS_SQL,
                (rs, rowNum) -> new DetailRow(
                        mapRow(rs, rowNum),
                        rs.getObject("inventory_id", UUID.class),
                        rs.getString("branch_state")
                ),
                productId, storeId);
        List<VehicleRuleResponse> rules = vehicleRules.forProduct(productId);
        List<ProductImageResponse> images = productImageRepository.findAllOfProducts(Set.of(productId)).stream()
                .map(productImageMapper::toResponse)
                .toList();
        List<PromotionRule> activePromotions = priceCalculator.activeRulesAt(OffsetDateTime.now(ZoneOffset.UTC));
        List<StoreProductDetail.Listing> listings = rows.stream()
                .map(detail -> {
                    ProductResponse listing = toResponse(detail.row(), product, rules, images, activePromotions);
                    return new StoreProductDetail.Listing(
                            detail.inventoryId(),
                            detail.row().branchId(),
                            detail.row().branchName(),
                            detail.branchState(),
                            listing.price(),
                            listing.effectivePrice(),
                            listing.appliedPromotionName(),
                            listing.quantity()
                    );
                })
                .toList();
        return new StoreProductDetail(
                product.getId(),
                product.getStoreId(),
                product.getName(),
                product.getSku(),
                product.getPartNumber(),
                product.getDescription(),
                product.getManufacturer().getId(),
                product.getManufacturer().getName(),
                product.getManufacturer().isApproved(),
                product.getCategory().getId(),
                product.getState().getCode(),
                product.isUniversal(),
                product.getAttributes(),
                rules,
                images,
                listings
        );
    }

    /** Listados visibles en el marketplace: producto, almacén y sucursal activos y con stock. */
    @Transactional(readOnly = true)
    public List<ProductResponse> publishedListings() {
        return publishedListings(VehicleFilter.none());
    }

    /**
     * Listados visibles en el marketplace filtrados por vehículo: solo productos con una regla de
     * {@code product_vehicle_rules} que cubra el vehículo (una regla con modelo/versión nulos cubre todo bajo ella).
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> publishedListings(VehicleFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return toResponses(jdbcTemplate.query(PUBLISHED_LISTINGS_SQL, ListingQueries::mapRow));
        }
        StringBuilder sql = new StringBuilder(PUBLISHED_LISTINGS_WHERE_SQL).append(VEHICLE_MATCH_MAKE_SQL);
        List<Object> args = new java.util.ArrayList<>();
        args.add(filter.makeId());
        if (filter.modelId() != null) {
            sql.append(VEHICLE_MATCH_MODEL_SQL);
            args.add(filter.modelId());
        }
        if (filter.versionId() != null) {
            sql.append(VEHICLE_MATCH_VERSION_SQL);
            args.add(filter.versionId());
        }
        sql.append(VEHICLE_MATCH_END_SQL).append(PUBLISHED_LISTINGS_ORDER_SQL);
        return toResponses(jdbcTemplate.query(sql.toString(), ListingQueries::mapRow, args.toArray()));
    }

    /** El listado publicado más barato del producto; si no hay ninguno, {@code PRODUCT_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public ProductResponse publishedListing(UUID productId) {
        return jdbcTemplate.query(PUBLISHED_LISTING_SQL, ListingQueries::mapRow, productId).stream()
                .findFirst()
                .flatMap(row -> toResponses(List.of(row)).stream().findFirst())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

    /**
     * Listados comprables: mismos filtros que el listado publicado (producto, almacén y sucursal {@code ACTIVE}, sin
     * borrar) pero SIN {@code i.stock > 0}, restringidos a los pares pedidos. Las claves sin listado no aparecen en el
     * mapa. El número de claves lo limita la capa web (máximo 50).
     */
    @Transactional(readOnly = true)
    public Map<ListingKey, ProductResponse> purchasableListings(Collection<ListingKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<ListingKey> distinctKeys = List.copyOf(new LinkedHashSet<>(keys));
        String values = String.join(", ", Collections.nCopies(distinctKeys.size(), "(?::uuid, ?::uuid)"));
        Object[] args = new Object[distinctKeys.size() * 2];
        for (int index = 0; index < distinctKeys.size(); index++) {
            args[index * 2] = distinctKeys.get(index).productId();
            args[index * 2 + 1] = distinctKeys.get(index).branchId();
        }
        List<ListingRow> rows = jdbcTemplate.query(
                PURCHASABLE_LISTINGS_SQL.replace(KEYS_PLACEHOLDER, values), ListingQueries::mapRow, args);
        return toResponses(rows).stream().collect(Collectors.toMap(
                response -> new ListingKey(response.id(), response.branchId()),
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public ProductOptionsResponse options() {
        return new ProductOptionsResponse(manufacturers(), categories());
    }

    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
        return manufacturerRepository.findAllByApprovedTrueOrderByNameAsc().stream()
                .map(item -> new ProductOptionsResponse.ManufacturerOption(item.getId(), item.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.CategoryOption> categories() {
        return categoryRepository.findAllByOrderByCodeAsc().stream()
                .map(item -> new ProductOptionsResponse.CategoryOption(item.getId(), item.getCode()))
                .toList();
    }

    /**
     * Lista completa de la tabla {@code compatibilities}. Obsoleta: los productos ya no se asocian a compatibilidades
     * sino a reglas de vehículo; se conserva solo para clientes antiguos hasta retirar la tabla.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
        return compatibilityService.options().stream()
                .map(item -> new ProductOptionsResponse.CompatibilityOption(
                        item.id(), item.make(), item.model(), item.version()))
                .toList();
    }

    private List<ProductResponse> toResponses(List<ListingRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> productIds = rows.stream().map(ListingRow::productId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepository.findAllWithDetails(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, List<VehicleRuleResponse>> rulesByProduct = vehicleRules.forProducts(productIds);
        Map<UUID, List<ProductImageResponse>> imagesByProduct = productImageRepository
                .findAllOfProducts(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId(),
                        Collectors.mapping(productImageMapper::toResponse, Collectors.toList())
                ));
        List<PromotionRule> activePromotions = priceCalculator.activeRulesAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Si el producto se borró (lógico) entre la consulta y la carga, se omite la fila en vez de fallar.
        return rows.stream()
                .filter(row -> productsById.get(row.productId()) != null)
                .map(row -> toResponse(
                        row,
                        productsById.get(row.productId()),
                        rulesByProduct.getOrDefault(row.productId(), List.of()),
                        imagesByProduct.getOrDefault(row.productId(), List.of()),
                        activePromotions))
                .toList();
    }

    private ProductResponse toResponse(
            ListingRow row,
            Product product,
            List<VehicleRuleResponse> rules,
            List<ProductImageResponse> images,
            List<PromotionRule> activePromotions
    ) {
        PriceQuote quote = priceCalculator.bestPrice(new PricingSubject(
                row.price(),
                product.getId(),
                product.getStoreId(),
                product.getCategory().getId(),
                product.getManufacturer().getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPartNumber(),
                product.getState().getCode(),
                product.getAttributes(),
                ProductVehicleRules.vehicleIds(rules)
        ), activePromotions);
        return new ProductResponse(
                product.getId(),
                row.branchStoreId(),
                row.branchId(),
                row.branchName(),
                product.getName(),
                product.getSku(),
                product.getPartNumber(),
                product.getDescription(),
                row.price(),
                quote.effectivePrice(),
                quote.discountAmount(),
                quote.promotionName(),
                row.stock(),
                product.getManufacturer().getId(),
                product.getManufacturer().getName(),
                product.getManufacturer().isApproved(),
                product.getCategory().getId(),
                product.getState().getCode(),
                product.isUniversal(),
                product.getAttributes(),
                rules,
                images
        );
    }

    private static ListingRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ListingRow(
                rs.getObject("product_id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getObject("branch_store_id", UUID.class),
                rs.getString("branch_name"),
                rs.getBigDecimal("price"),
                rs.getObject("stock", Integer.class)
        );
    }

    /** Fila de listado con el id del inventario y el estado de la sucursal (detalle del producto). */
    record DetailRow(ListingRow row, UUID inventoryId, String branchState) {
    }

    /** Fila de listado: un producto en una sucursal. */
    record ListingRow(
            UUID productId,
            UUID branchId,
            UUID branchStoreId,
            String branchName,
            BigDecimal price,
            Integer stock
    ) {
    }
}
