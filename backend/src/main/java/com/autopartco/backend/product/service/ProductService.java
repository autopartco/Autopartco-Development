package com.autopartco.backend.product.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.product.dto.ProductCreateRequest;
import com.autopartco.backend.product.dto.ProductPublicationRequest;
import com.autopartco.backend.product.dto.ProductOptionsResponse;
import com.autopartco.backend.product.dto.ProductResponse;
import com.autopartco.backend.product.dto.ProductUpdateRequest;
import com.autopartco.backend.product.dto.ProductImageResponse;
import com.autopartco.backend.product.entity.Category;
import com.autopartco.backend.product.entity.Compatibility;
import com.autopartco.backend.product.entity.Inventory;
import com.autopartco.backend.product.entity.Manufacturer;
import com.autopartco.backend.product.entity.Product;
import com.autopartco.backend.product.entity.Promotion;
import com.autopartco.backend.product.repository.CategoryRepository;
import com.autopartco.backend.product.repository.CompatibilityRepository;
import com.autopartco.backend.product.repository.InventoryRepository;
import com.autopartco.backend.product.repository.ManufacturerRepository;
import com.autopartco.backend.product.repository.ProductRepository;
import com.autopartco.backend.product.repository.ProductImageRepository;
import com.autopartco.backend.product.repository.PromotionRepository;
import com.autopartco.backend.product.mapper.ProductImageMapper;
import com.autopartco.backend.store.entity.Branch;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.store.repository.BranchRepository;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.store.service.StoreAccessService;
import com.autopartco.backend.store.service.StorePermissionCodes;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageMapper productImageMapper;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CategoryRepository categoryRepository;
    private final CompatibilityRepository compatibilityRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final StateRepository stateRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionPricingService promotionPricingService;
    private final SellerPlanEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID storeId, String email) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_READ);
        return toResponses(inventoryRepository.findAllInStore(storeId));
    }

    @Transactional
    public ProductResponse create(UUID storeId, String email, ProductCreateRequest request) {
        var store = requirePermission(storeId, email, StorePermissionCodes.PRODUCT_CREATE);
        storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        entitlementService.requireCapacity(storeId, SellerPlanEntitlementService.Capacity.PRODUCTS, 1);
        Branch branch = findBranch(storeId, request.branchId());
        Product product = Product.builder()
                .store(store)
                .name(request.name().trim())
                .partNumber(clean(request.partNumber()))
                .description(request.description().trim())
                .manufacturer(findManufacturer(request.manufacturerId()))
                .category(findCategory(request.categoryId()))
                .state(findState(ACTIVE))
                .attributes(normalizeAttributes(request.attributes()))
                .compatibilities(findCompatibilities(request.compatibilityIds()))
                .build();

        Product saved = productRepository.save(product);
        Inventory inventory = Inventory.builder()
                .branch(branch)
                .product(saved)
                .stock(request.quantity())
                .price(request.price())
                .build();

        return toResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public ProductResponse update(
            UUID storeId,
            UUID branchId,
            UUID productId,
            String email,
            ProductUpdateRequest request
    ) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_UPDATE);
        Inventory inventory = findListing(storeId, branchId, productId);
        Product product = inventory.getProduct();

        if (request.name() != null) product.setName(request.name().trim());
        if (request.partNumber() != null) product.setPartNumber(clean(request.partNumber()));
        if (request.description() != null) product.setDescription(request.description().trim());
        if (request.price() != null) inventory.setPrice(request.price());
        if (request.quantity() != null) inventory.setStock(request.quantity());
        if (request.manufacturerId() != null) product.setManufacturer(findManufacturer(request.manufacturerId()));
        if (request.categoryId() != null) product.setCategory(findCategory(request.categoryId()));
        if (request.attributes() != null) product.setAttributes(normalizeAttributes(request.attributes()));
        if (request.compatibilityIds() != null) {
            product.setCompatibilities(findCompatibilities(request.compatibilityIds()));
        }

        return toResponse(inventory);
    }

    @Transactional
    public void publish(
            UUID storeId,
            UUID productId,
            String email,
            ProductPublicationRequest request
    ) {
        requirePermission(storeId, email, StorePermissionCodes.PRODUCT_PUBLISH);
        if (request.published()) {
            entitlementService.requireSubscriptionActive(storeId);
        }
            Product product = productRepository.findOwned(storeId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
        product.setState(findState(request.published() ? ACTIVE : INACTIVE));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> published() {
        return toResponses(inventoryRepository.findPublished());
    }

    @Transactional(readOnly = true)
    public ProductResponse published(UUID productId) {
        return inventoryRepository.findPublishedByProductId(productId).stream()
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

    @Transactional(readOnly = true)
    public Product getOwnedProductForUpdate(
            UUID storeId,
            UUID productId,
            String email,
            String permissionCode
    ) {
        requirePermission(storeId, email, permissionCode);
        return productRepository.findOwnedForUpdate(storeId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

        @Transactional(readOnly = true)
        public ProductOptionsResponse options() {
        return new ProductOptionsResponse(
                manufacturers(),
                categories(),
                compatibilities()
        );
        }

            @Transactional(readOnly = true)
            public List<ProductOptionsResponse.ManufacturerOption> manufacturers() {
            return manufacturerRepository.findAllByOrderByNameAsc().stream()
                .map(item -> new ProductOptionsResponse.ManufacturerOption(item.getId(), item.getName()))
                .toList();
            }

            @Transactional(readOnly = true)
            public List<ProductOptionsResponse.CategoryOption> categories() {
            return categoryRepository.findAllByOrderByCodeAsc().stream()
                .map(item -> new ProductOptionsResponse.CategoryOption(item.getId(), item.getCode()))
                .toList();
            }

            @Transactional(readOnly = true)
            public List<ProductOptionsResponse.CompatibilityOption> compatibilities() {
            return compatibilityRepository.findOptions().stream()
                .map(item -> new ProductOptionsResponse.CompatibilityOption(
                    item.getId(), item.getMake(), item.getModel(), item.getVersion()))
                .toList();
            }

    private Store requirePermission(UUID storeId, String email, String permissionCode) {
        storeAccessService.requirePermission(email, storeId, permissionCode);
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private Branch findBranch(UUID storeId, UUID branchId) {
        Branch branch = branchRepository.findInStore(branchId, storeId)
                .orElseThrow(() -> new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal."));
        if (!ACTIVE.equals(branch.getState().getCode())) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal activa.");
        }
        return branch;
    }

    private Inventory findListing(UUID storeId, UUID branchId, UUID productId) {
        return inventoryRepository.findInStore(storeId, branchId, productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

    private Manufacturer findManufacturer(UUID id) {
        return manufacturerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MANUFACTURER_NOT_FOUND", "No se encontró el fabricante."));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "No se encontró la categoría."));
    }

    private Set<Compatibility> findCompatibilities(Set<UUID> ids) {
        Set<Compatibility> results = compatibilityRepository.findAllById(ids).stream().collect(Collectors.toSet());
        if (results.size() != ids.size()) {
            throw new NotFoundException("COMPATIBILITY_NOT_FOUND", "Una o más compatibilidades no existen.");
        }
        return results;
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("PRODUCT_STATE_NOT_FOUND", "No está configurado el estado."));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> normalized = new HashMap<>();
        attributes.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > 100) {
                throw new BadRequestException(
                        "INVALID_PRODUCT_ATTRIBUTE",
                        "Los atributos deben tener nombres no vacíos de máximo 100 caracteres."
                );
            }
            if (value != null) {
                normalized.put(key.trim(), value);
            }
        });
        return normalized;
    }

    @SuppressWarnings("null")
    private ProductResponse toResponse(Inventory inventory) {
        Product product = inventory.getProduct();
        List<ProductImageResponse> images = productImageRepository
                .findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(product.getId())
                .stream()
                .map(productImageMapper::toResponse)
                .toList();
        return toResponse(inventory, images, promotionRepository.findActiveAt(
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)));
    }

    private List<ProductResponse> toResponses(List<Inventory> inventories) {
        if (inventories.isEmpty()) {
            return List.of();
        }
        Set<UUID> productIds = inventories.stream()
                .map(inventory -> inventory.getProduct().getId())
                .collect(Collectors.toSet());
        Map<UUID, List<ProductImageResponse>> imagesByProduct = productImageRepository
                .findByProduct_IdInOrderByProduct_IdAscDisplayOrderAscCreatedAtAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId(),
                        Collectors.mapping(productImageMapper::toResponse, Collectors.toList())
                ));
        List<Promotion> activePromotions = promotionRepository.findActiveAt(
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return inventories.stream()
                .map(inventory -> toResponse(inventory,
                        imagesByProduct.getOrDefault(inventory.getProduct().getId(), List.of()),
                        activePromotions))
                .toList();
    }

    private ProductResponse toResponse(
            Inventory inventory,
            List<ProductImageResponse> images,
            List<Promotion> activePromotions
    ) {
        Product product = inventory.getProduct();
        PromotionPricingService.PriceQuote quote = promotionPricingService.bestPrice(
                inventory, activePromotions);
        return new ProductResponse(
                product.getId(),
                inventory.getBranch().getStore().getId(),
                inventory.getBranch().getId(),
                inventory.getBranch().getName(),
                product.getName(),
                product.getPartNumber(),
                product.getDescription(),
                inventory.getPrice(),
                quote.effectivePrice(),
                quote.discountAmount(),
                quote.promotionName(),
                inventory.getStock(),
                product.getManufacturer().getId(),
                product.getCategory().getId(),
                product.getState().getCode(),
                product.getAttributes(),
                product.getCompatibilities().stream().map(Compatibility::getId).collect(Collectors.toSet()),
                images
        );
    }
}