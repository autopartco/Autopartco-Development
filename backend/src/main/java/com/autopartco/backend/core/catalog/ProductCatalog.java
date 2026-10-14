package com.autopartco.backend.core.catalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.vehicle.CompatibilityService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Alta, edición, publicación y borrado de productos de un almacén, siempre acotados por {@code storeId}. No resuelve
 * permisos ni derechos de plan: lo hacen los casos de uso de los productos antes de llamar.
 */
@Service
@AllArgsConstructor
public class ProductCatalog {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CategoryRepository categoryRepository;
    private final StateRepository stateRepository;
    private final CompatibilityService compatibilityService;
    private final BranchService branchService;
    private final StockService stockService;
    private final ProductImages productImages;
    private final ListingQueries listingQueries;

    /** Crea el producto (activo) y su listado en la sucursal indicada, que debe ser del almacén y estar activa. */
    @Transactional
    public ProductResponse create(UUID storeId, ProductCreateRequest request) {
        branchService.requireActiveInStore(storeId, request.branchId());
        String sku = clean(request.sku());
        requireSkuFree(storeId, sku, null);
        Product product = Product.builder()
                .storeId(storeId)
                .name(request.name().trim())
                .sku(sku)
                .partNumber(clean(request.partNumber()))
                .description(request.description().trim())
                .manufacturer(findManufacturer(request.manufacturerId()))
                .category(findCategory(request.categoryId()))
                .state(findState(StateCodes.ACTIVE))
                .attributes(normalizeAttributes(request.attributes()))
                .compatibilityIds(requireCompatibilities(request.compatibilityIds()))
                .build();

        Product saved = productRepository.save(product);
        stockService.createListing(request.branchId(), saved.getId(), request.quantity(), request.price());
        return reloadListing(storeId, request.branchId(), saved.getId());
    }

    /** Edita el producto y su listado en la sucursal; sin listado del almacén en esa sucursal, {@code PRODUCT_NOT_FOUND}. */
    @Transactional
    public ProductResponse update(UUID storeId, UUID branchId, UUID productId, ProductUpdateRequest request) {
        if (listingQueries.storeListing(storeId, branchId, productId).isEmpty()) {
            throw productNotFound();
        }
        Product product = productRepository.findOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);

        if (request.name() != null) product.setName(request.name().trim());
        if (request.sku() != null) {
            // Semántica existente: "" o solo espacios borra el SKU (igual que partNumber); null = no cambiar.
            String sku = clean(request.sku());
            requireSkuFree(storeId, sku, productId);
            product.setSku(sku);
        }
        if (request.partNumber() != null) product.setPartNumber(clean(request.partNumber()));
        if (request.description() != null) product.setDescription(request.description().trim());
        if (request.price() != null || request.quantity() != null) {
            stockService.updateListing(branchId, productId, request.price(), request.quantity());
        }
        if (request.manufacturerId() != null) product.setManufacturer(findManufacturer(request.manufacturerId()));
        if (request.categoryId() != null) product.setCategory(findCategory(request.categoryId()));
        if (request.attributes() != null) product.setAttributes(normalizeAttributes(request.attributes()));
        if (request.compatibilityIds() != null) {
            product.setCompatibilityIds(requireCompatibilities(request.compatibilityIds()));
        }

        return reloadListing(storeId, branchId, productId);
    }

    /** Publica ({@code ACTIVE}) o retira ({@code INACTIVE}) el producto del almacén. */
    @Transactional
    public void setPublished(UUID storeId, UUID productId, boolean published) {
        Product product = productRepository.findOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);
        product.setState(findState(published ? StateCodes.ACTIVE : StateCodes.INACTIVE));
    }

    /** Bloquea ({@code FOR UPDATE}) el producto del almacén; si no existe, {@code PRODUCT_NOT_FOUND}. */
    @Transactional
    public void requireOwnedForUpdate(UUID storeId, UUID productId) {
        productRepository.lockOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);
    }

    /**
     * Borra el producto del almacén: imágenes (Storage y filas, con restauración si la transacción se revierte),
     * listados (borrado lógico) y el producto (borrado lógico).
     */
    @Transactional
    public void delete(UUID storeId, UUID productId) {
        Product product = productRepository.lockOwned(storeId, productId)
                .orElseThrow(ProductCatalog::productNotFound);
        productImages.deleteAllForProduct(productId);
        stockService.removeAllForProduct(productId);
        productRepository.delete(product);
        productRepository.flush();
    }

    @Transactional(readOnly = true)
    public long countInStore(UUID storeId) {
        return productRepository.countProductsInStore(storeId);
    }

    private ProductResponse reloadListing(UUID storeId, UUID branchId, UUID productId) {
        // ListingQueries usa SQL nativo: hay que volcar los cambios pendientes antes de leer.
        productRepository.flush();
        return listingQueries.storeListing(storeId, branchId, productId).orElseThrow(ProductCatalog::productNotFound);
    }

    private Manufacturer findManufacturer(UUID id) {
        return manufacturerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MANUFACTURER_NOT_FOUND", "No se encontró el fabricante."));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "No se encontró la categoría."));
    }

    private Set<UUID> requireCompatibilities(Set<UUID> ids) {
        compatibilityService.requireAllExist(ids);
        return new HashSet<>(ids);
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("PRODUCT_STATE_NOT_FOUND", "No está configurado el estado."));
    }

    /** El SKU es opcional; si se informa, no puede repetirse en otro producto del mismo almacén. */
    private void requireSkuFree(UUID storeId, String sku, UUID excludeProductId) {
        if (sku != null && productRepository.skuInUse(storeId, sku, excludeProductId)) {
            throw new ConflictException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un repuesto con ese SKU en el almacén.");
        }
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

    private static NotFoundException productNotFound() {
        return new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto.");
    }
}
