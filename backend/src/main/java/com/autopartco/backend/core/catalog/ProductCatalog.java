package com.autopartco.backend.core.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.catalog.internal.Category;
import com.autopartco.backend.core.catalog.internal.CategoryRepository;
import com.autopartco.backend.core.catalog.internal.Manufacturer;
import com.autopartco.backend.core.catalog.internal.ManufacturerRepository;
import com.autopartco.backend.core.catalog.internal.Product;
import com.autopartco.backend.core.catalog.internal.ProductImageValidator.ValidatedImage;
import com.autopartco.backend.core.catalog.internal.ProductRepository;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Alta, edición, publicación y borrado de productos de un almacén, siempre acotados por {@code storeId}. No resuelve
 * permisos ni derechos de plan: lo hacen los casos de uso de los productos antes de llamar (y le pasan si la
 * suscripción está activa, requisito de publicación).
 *
 * <p>Alta y edición son atómicas y en dos fases: primero se valida todo sin escribir (sucursal, imágenes, fabricante,
 * categoría, reglas, galería y, si se publica, requisitos de publicación); después se escribe. Cualquier fallo revierte
 * la transacción y {@link ProductImages} limpia lo subido a Storage o restaura lo borrado.</p>
 */
@Service
@AllArgsConstructor
public class ProductCatalog {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CategoryRepository categoryRepository;
    private final StateRepository stateRepository;
    private final BranchService branchService;
    private final StockService stockService;
    private final ProductImages productImages;
    private final ListingQueries listingQueries;
    private final ProductVehicleRules vehicleRules;
    private final StoreManufacturers storeManufacturers;

    /**
     * Alta atómica: producto, listado en la sucursal (del almacén y activa), fabricante nuevo si se indicó, reglas de
     * vehículo, imágenes y, con {@code publish=true}, publicación. Si se pide publicar y falta algún requisito, 409
     * {@code PRODUCT_NOT_PUBLISHABLE} sin crear ni subir nada.
     */
    @Transactional
    public ProductResponse create(
            UUID storeId,
            ProductCreateRequest request,
            List<MultipartFile> files,
            boolean subscriptionActive
    ) {
        // Fase 1: validaciones y lecturas.
        branchService.requireActiveInStore(storeId, request.branchId());
        List<ValidatedImage> images = productImages.validateAll(files);
        String mainToken = null;
        if (request.mainImageIndex() != null) {
            if (request.mainImageIndex() >= images.size()) {
                throw invalidMainImage();
            }
            mainToken = ProductImages.NEW_TOKEN_PREFIX + request.mainImageIndex();
        }
        ManufacturerChoice manufacturer = chooseManufacturer(storeId, request.manufacturerId(),
                request.newManufacturerName(), request.confirmNewManufacturer());
        Category category = findCategory(request.categoryId());
        requireNoRulesIfUniversal(request.universalRequested(), request.vehicleRules());
        List<VehicleRuleRequest> rules = vehicleRules.normalize(request.vehicleRules());
        Map<String, Object> attributes = normalizeAttributes(request.attributes());
        ProductImages.GalleryPlan gallery = productImages.planGallery(null, Set.of(), images, null, mainToken);
        boolean publish = request.publishRequested();
        if (publish) {
            requirePublishable(gallery.finalCount(), request.universalRequested() || !rules.isEmpty(), true,
                    subscriptionActive);
        }
        State state = findState(publish ? StateCodes.ACTIVE : StateCodes.INACTIVE);

        // Fase 2: escrituras.
        Product saved = productRepository.save(Product.builder()
                .storeId(storeId)
                .name(request.name().trim())
                .sku(clean(request.sku()))
                .partNumber(clean(request.partNumber()))
                .description(request.description().trim())
                .manufacturer(manufacturer.materialize(storeId, storeManufacturers))
                .category(category)
                .state(state)
                .attributes(attributes)
                .universal(request.universalRequested())
                .build());
        stockService.createListing(request.branchId(), saved.getId(), request.quantity(), request.price());
        vehicleRules.replace(saved.getId(), rules);
        if (!images.isEmpty()) {
            productImages.applyGallery(saved, gallery);
        }
        return reloadListing(storeId, request.branchId(), saved.getId());
    }

    /**
     * Edición atómica del producto y de su listado en la sucursal (sin listado del almacén en esa sucursal,
     * {@code PRODUCT_NOT_FOUND}). {@code vehicleRules} reemplaza el conjunto; la galería se recalcula con
     * {@code removeImageIds}/{@code imageOrder}/{@code mainImage} y {@code files}. {@code publish=true} exige los
     * requisitos sobre el estado final; si faltan, 409 {@code PRODUCT_NOT_PUBLISHABLE} sin cambiar nada.
     */
    @Transactional
    public ProductResponse update(
            UUID storeId,
            UUID branchId,
            UUID productId,
            ProductUpdateRequest request,
            List<MultipartFile> files,
            boolean subscriptionActive
    ) {
        if (listingQueries.storeListing(storeId, branchId, productId).isEmpty()) {
            throw productNotFound();
        }
        Product product = productRepository.lockOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);

        // Fase 1: validaciones y lecturas.
        List<ValidatedImage> images = productImages.validateAll(files);
        boolean galleryTouched = !images.isEmpty() || request.changesGallery();
        if (!request.changesProductData() && !galleryTouched && request.publish() == null) {
            throw new BadRequestException("NO_CHANGES", "Debe proporcionar al menos un campo para actualizar.");
        }
        ManufacturerChoice manufacturer = request.manufacturerId() != null || request.newManufacturerName() != null
                ? chooseManufacturer(storeId, request.manufacturerId(), request.newManufacturerName(),
                        request.confirmNewManufacturer())
                : null;
        Category category = request.categoryId() != null ? findCategory(request.categoryId()) : null;
        boolean universal = request.universal() != null ? request.universal() : product.isUniversal();
        requireNoRulesIfUniversal(universal, request.vehicleRules());
        List<VehicleRuleRequest> rules = request.vehicleRules() != null
                ? vehicleRules.normalize(request.vehicleRules())
                : null;
        Map<String, Object> attributes = request.attributes() != null ? normalizeAttributes(request.attributes()) : null;
        ProductImages.GalleryPlan gallery = galleryTouched
                ? productImages.planGallery(productId, request.removeImageIds(), images, request.imageOrder(),
                        request.mainImage())
                : null;
        if (Boolean.TRUE.equals(request.publish())) {
            long imageCount = gallery != null ? gallery.finalCount() : productImages.count(productId);
            // Universal: no tiene reglas (las existentes se borran al marcarlo); si no, las finales cuentan.
            boolean vehiclesOk = universal
                    || (rules != null ? !rules.isEmpty() : !vehicleRules.forProduct(productId).isEmpty());
            boolean manufacturerUsable = manufacturer != null || currentManufacturerUsable(storeId, product);
            requirePublishable(imageCount, vehiclesOk, manufacturerUsable, subscriptionActive);
        }
        State state = request.publish() != null
                ? findState(request.publish() ? StateCodes.ACTIVE : StateCodes.INACTIVE)
                : null;

        // Fase 2: escrituras.
        if (request.name() != null) product.setName(request.name().trim());
        // "" o solo espacios borra SKU / número de parte; null = no cambiar.
        if (request.sku() != null) product.setSku(clean(request.sku()));
        if (request.partNumber() != null) product.setPartNumber(clean(request.partNumber()));
        if (request.description() != null) product.setDescription(request.description().trim());
        if (request.price() != null || request.quantity() != null) {
            stockService.updateListing(branchId, productId, request.price(), request.quantity());
        }
        if (manufacturer != null) product.setManufacturer(manufacturer.materialize(storeId, storeManufacturers));
        if (category != null) product.setCategory(category);
        if (attributes != null) product.setAttributes(attributes);
        if (request.universal() != null) product.setUniversal(universal);
        if (universal && (request.universal() != null || rules != null)) {
            vehicleRules.replace(productId, List.of());
        } else if (rules != null) {
            vehicleRules.replace(productId, rules);
        }
        if (gallery != null) productImages.applyGallery(product, gallery);
        if (state != null) product.setState(state);

        return reloadListing(storeId, branchId, productId);
    }

    /**
     * Publica ({@code ACTIVE}) el producto del almacén si cumple los requisitos (al menos una imagen y una regla de
     * vehículo o ser universal, fabricante aprobado o pendiente propio, suscripción activa); si no, 409 {@code PRODUCT_NOT_PUBLISHABLE}.
     */
    @Transactional
    public void publish(UUID storeId, UUID productId, boolean subscriptionActive) {
        Product product = productRepository.lockOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);
        requirePublishable(productImages.count(productId),
                product.isUniversal() || !vehicleRules.forProduct(productId).isEmpty(),
                currentManufacturerUsable(storeId, product), subscriptionActive);
        product.setState(findState(StateCodes.ACTIVE));
    }

    /** Retira ({@code INACTIVE}) el producto del almacén; no exige requisitos. */
    @Transactional
    public void unpublish(UUID storeId, UUID productId) {
        Product product = productRepository.findOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);
        product.setState(findState(StateCodes.INACTIVE));
    }

    /** Bloquea ({@code FOR UPDATE}) el producto del almacén; si no existe, {@code PRODUCT_NOT_FOUND}. */
    @Transactional
    public void requireOwnedForUpdate(UUID storeId, UUID productId) {
        productRepository.lockOwned(storeId, productId).orElseThrow(ProductCatalog::productNotFound);
    }

    /**
     * Borra el producto del almacén: imágenes (Storage y filas, con restauración si la transacción se revierte),
     * reglas de vehículo, listados (borrado lógico) y el producto (borrado lógico).
     */
    @Transactional
    public void delete(UUID storeId, UUID productId) {
        Product product = productRepository.lockOwned(storeId, productId)
                .orElseThrow(ProductCatalog::productNotFound);
        productImages.deleteAllForProduct(productId);
        vehicleRules.replace(productId, List.of());
        stockService.removeAllForProduct(productId);
        productRepository.delete(product);
        productRepository.flush();
    }

    /** Detalle del producto del almacén con sus listados por sucursal. */
    @Transactional(readOnly = true)
    public StoreProductDetail detail(UUID storeId, UUID productId) {
        return listingQueries.storeProductDetail(storeId, productId);
    }

    /** Agrega el producto a otra sucursal activa del almacén. */
    @Transactional
    public StoreProductDetail addListing(UUID storeId, UUID productId, ProductListingRequest request) {
        requireOwnedForUpdate(storeId, productId);
        branchService.requireActiveInStore(storeId, request.branchId());
        stockService.addListing(request.branchId(), productId, request.stock(), request.price());
        productRepository.flush();
        return listingQueries.storeProductDetail(storeId, productId);
    }

    /** Quita el listado de una sucursal. El último listado no se quita: para eso se borra el producto. */
    @Transactional
    public void removeListing(UUID storeId, UUID productId, UUID branchId) {
        requireOwnedForUpdate(storeId, productId);
        stockService.removeListing(storeId, branchId, productId);
        // Si no quedó ninguno, el lanzamiento revierte el borrado lógico de esta transacción.
        if (stockService.countListingsOfProduct(productId) == 0) {
            throw new ConflictException(
                    "LAST_LISTING",
                    "Es el único listado del producto. Para quitarlo, borra el producto."
            );
        }
    }

    @Transactional(readOnly = true)
    public long countInStore(UUID storeId) {
        return productRepository.countProductsInStore(storeId);
    }

    /**
     * Requisitos de publicación; si falta alguno, 409 {@code PRODUCT_NOT_PUBLISHABLE} con un {@link PublicationIssue}
     * por requisito. El stock 0 no bloquea.
     */
    static void requirePublishable(long imageCount, boolean vehiclesOk, boolean manufacturerUsable,
            boolean subscriptionActive) {
        List<PublicationIssue> issues = new ArrayList<>();
        if (imageCount < 1) issues.add(PublicationIssue.noImages());
        if (!vehiclesOk) issues.add(PublicationIssue.noVehicles());
        if (!manufacturerUsable) issues.add(PublicationIssue.manufacturerInvalid());
        if (!subscriptionActive) issues.add(PublicationIssue.subscriptionInactive());
        if (!issues.isEmpty()) {
            throw new ConflictException("PRODUCT_NOT_PUBLISHABLE",
                    "El repuesto no cumple los requisitos para publicarse.", issues);
        }
    }

    /** Un producto universal no lleva reglas de vehículo: 400 {@code VALIDATION_ERROR} con detalle en {@code vehicleRules}. */
    private static void requireNoRulesIfUniversal(boolean universal, List<VehicleRuleRequest> rules) {
        if (universal && rules != null && !rules.isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "Los datos enviados no son válidos.",
                    Map.of("vehicleRules", "Un producto universal no lleva reglas de vehículo."));
        }
    }

    private ProductResponse reloadListing(UUID storeId, UUID branchId, UUID productId) {
        // ListingQueries usa SQL nativo: hay que volcar los cambios pendientes antes de leer.
        productRepository.flush();
        return listingQueries.storeListing(storeId, branchId, productId).orElseThrow(ProductCatalog::productNotFound);
    }

    /**
     * Fabricante elegido: un id visible al almacén (aprobado o pendiente propio; si no, {@code MANUFACTURER_NOT_FOUND}
     * para no revelar pendientes ajenos) o un nombre nuevo, resuelto sin escribir por {@link StoreManufacturers}.
     */
    private ManufacturerChoice chooseManufacturer(UUID storeId, UUID manufacturerId, String newName, Boolean confirm) {
        if (manufacturerId != null && newName != null) {
            throw new BadRequestException("MANUFACTURER_CHOICE_INVALID",
                    "Indica un fabricante existente o el nombre de uno nuevo, no ambos.");
        }
        if (manufacturerId != null) {
            return new ManufacturerChoice(findManufacturer(storeId, manufacturerId), null);
        }
        if (newName == null) {
            throw new BadRequestException("MANUFACTURER_CHOICE_INVALID", "Indica el fabricante del repuesto.");
        }
        return new ManufacturerChoice(null, storeManufacturers.planNew(storeId, newName, confirm));
    }

    /** Solo fabricantes aprobados o pendientes sugeridos por este almacén (evita IDOR entre almacenes). */
    private Manufacturer findManufacturer(UUID storeId, UUID id) {
        return manufacturerRepository.findVisibleToStore(id, storeId)
                .orElseThrow(() -> new NotFoundException("MANUFACTURER_NOT_FOUND", "No se encontró el fabricante."));
    }

    /** El fabricante actual sigue vigente y es aprobado o pendiente propio. */
    private boolean currentManufacturerUsable(UUID storeId, Product product) {
        return product.getManufacturer() != null
                && manufacturerRepository.findVisibleToStore(product.getManufacturer().getId(), storeId).isPresent();
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "No se encontró la categoría."));
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("PRODUCT_STATE_NOT_FOUND", "No está configurado el estado."));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static final int MAX_ATTRIBUTES = 50;
    static final int MAX_ATTRIBUTE_KEY = 100;
    static final int MAX_ATTRIBUTE_TEXT = 500;
    static final int MAX_ATTRIBUTES_BYTES = 8 * 1024;

    /**
     * Atributos libres (jsonb): hasta 50 claves de 100 caracteres, valores escalares (texto de hasta 500 caracteres,
     * número o booleano; sin mapas ni listas) y 8 KB en total (UTF-8, claves y valores). Si no, 400
     * {@code INVALID_PRODUCT_ATTRIBUTE}.
     */
    static Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return new HashMap<>();
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw invalidAttribute("Los atributos admiten como máximo " + MAX_ATTRIBUTES + " entradas.");
        }
        Map<String, Object> normalized = new HashMap<>();
        long bytes = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > MAX_ATTRIBUTE_KEY) {
                throw invalidAttribute("Los atributos deben tener nombres no vacíos de máximo "
                        + MAX_ATTRIBUTE_KEY + " caracteres.");
            }
            if (value == null) {
                continue;
            }
            if (value instanceof String text) {
                if (text.length() > MAX_ATTRIBUTE_TEXT) {
                    throw invalidAttribute("El valor del atributo \"" + key.trim() + "\" supera los "
                            + MAX_ATTRIBUTE_TEXT + " caracteres.");
                }
            } else if (!(value instanceof Number || value instanceof Boolean)) {
                throw invalidAttribute("El valor del atributo \"" + key.trim()
                        + "\" debe ser texto, número o booleano (no se admiten objetos ni listas).");
            }
            bytes += key.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 6;
            normalized.put(key.trim(), value);
        }
        if (bytes > MAX_ATTRIBUTES_BYTES) {
            throw invalidAttribute("Los atributos superan el tamaño máximo de 8 KB.");
        }
        return normalized;
    }

    private static BadRequestException invalidAttribute(String message) {
        return new BadRequestException("INVALID_PRODUCT_ATTRIBUTE", message);
    }

    private static BadRequestException invalidMainImage() {
        return new BadRequestException("INVALID_PRODUCT_IMAGE_MAIN",
                "La imagen principal debe ser una de las imágenes del repuesto.");
    }

    private static NotFoundException productNotFound() {
        return new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto.");
    }

    /** Fabricante existente ya validado o plan de uno nuevo (que se crea en la fase de escritura). */
    private record ManufacturerChoice(Manufacturer existing, StoreManufacturers.NewManufacturer plan) {
        Manufacturer materialize(UUID storeId, StoreManufacturers manufacturers) {
            return existing != null ? existing : manufacturers.materialize(storeId, plan);
        }
    }
}
