package com.autopartco.backend.storesuite.products;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.catalog.ProductCatalog;
import com.autopartco.backend.core.catalog.ProductCatalog.BulkSkip;
import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.storesuite.inventory.InventoryActor;

import lombok.AllArgsConstructor;

/**
 * Acciones masivas sobre productos del almacén. Permiso por acción, verificado antes de tocar nada:
 * {@code PUBLISH}/{@code UNPUBLISH} → {@code PRODUCT_PUBLISH}, {@code DELETE} → {@code PRODUCT_DELETE},
 * {@code PRICE} → {@code PRODUCT_UPDATE}.
 *
 * <ul>
 *   <li>{@code PUBLISH}/{@code UNPUBLISH}: procesan los que pueden y devuelven el resto en {@code skipped}
 *       ({@code PRODUCT_NOT_FOUND}, {@code ALREADY_PUBLISHED}, {@code ALREADY_UNPUBLISHED} o el requisito de
 *       publicación incumplido: {@code NO_IMAGES}, {@code NO_VEHICLES}, {@code MANUFACTURER_INVALID},
 *       {@code SUBSCRIPTION_INACTIVE}).</li>
 *   <li>{@code DELETE} y {@code PRICE}: atómicos. Un producto de otro almacén es 404 {@code PRODUCT_NOT_FOUND}; en
 *       {@code PRICE}, uno sin listado en la sucursal es 404 {@code LISTING_NOT_FOUND}; un precio resultante fuera de
 *       rango es 400 {@code INVALID_PRICE}. Nada se aplica si algo falla.</li>
 * </ul>
 * El precio se redondea a pesos enteros (mitad hacia arriba).
 */
@Service
@AllArgsConstructor
public class BulkProductAction {
    public static final int MAX_PRODUCTS = 500;
    public static final int MAX_DELETE = 50;
    static final BigDecimal MIN_PERCENT = new BigDecimal("-90");
    static final BigDecimal MAX_PERCENT = new BigDecimal("500");
    static final BigDecimal MAX_PRICE = new BigDecimal("9999999999");

    private final StoreAccess storeAccess;
    private final StoreService storeService;
    private final BranchService branchService;
    private final PlanEntitlements planEntitlements;
    private final ProductCatalog productCatalog;
    private final InventoryQueries inventoryQueries;
    private final StockService stockService;
    private final InventoryActor inventoryActor;

    @Transactional
    public BulkProductResult execute(UUID storeId, String email, BulkProductRequest request) {
        String action = request.action();
        storeAccess.requirePermission(email, storeId, permissionFor(action));
        storeService.requireExists(storeId);
        List<UUID> ids = request.productIds().stream().distinct().sorted().toList();
        if (ids.size() > MAX_PRODUCTS) {
            throw new BadRequestException("TOO_MANY_PRODUCTS", "Selecciona como máximo 500 productos.");
        }
        if ("DELETE".equals(action) && ids.size() > MAX_DELETE) {
            throw new BadRequestException("BULK_DELETE_LIMIT",
                    "Elimina como máximo " + MAX_DELETE + " productos por solicitud.",
                    java.util.Map.of("max", MAX_DELETE));
        }
        return switch (action) {
            case "PUBLISH" -> result(ids, productCatalog.publishMany(storeId, ids,
                    planEntitlements.isSubscriptionActive(storeId)));
            case "UNPUBLISH" -> result(ids, productCatalog.unpublishMany(storeId, ids));
            case "DELETE" -> delete(storeId, ids);
            case "PRICE" -> price(storeId, email, ids, request);
            default -> throw new BadRequestException("INVALID_ACTION", "Acción no válida.");
        };
    }

    private static String permissionFor(String action) {
        return switch (action) {
            case "PUBLISH", "UNPUBLISH" -> StorePermissionCodes.PRODUCT_PUBLISH;
            case "DELETE" -> StorePermissionCodes.PRODUCT_DELETE;
            case "PRICE" -> StorePermissionCodes.PRODUCT_UPDATE;
            default -> throw new BadRequestException("INVALID_ACTION", "Acción no válida.");
        };
    }

    private static BulkProductResult result(List<UUID> ids, List<BulkSkip> skipped) {
        return new BulkProductResult(ids.size() - skipped.size(),
                skipped.stream().map(skip -> new BulkProductResult.Skipped(skip.productId(), skip.code())).toList());
    }

    private BulkProductResult delete(UUID storeId, List<UUID> ids) {
        // Bloquea y comprueba todos antes de borrar nada (ni tocar Storage): uno ajeno es 404 y no se aplica nada.
        ids.forEach(id -> productCatalog.requireOwnedForUpdate(storeId, id));
        ids.forEach(id -> productCatalog.delete(storeId, id));
        return new BulkProductResult(ids.size(), List.of());
    }

    private BulkProductResult price(UUID storeId, String email, List<UUID> ids, BulkProductRequest request) {
        branchService.requireActiveInStore(storeId, request.branchId());
        ids.forEach(id -> productCatalog.requireOwnedForUpdate(storeId, id));
        Map<UUID, BigDecimal> current = inventoryQueries.prices(storeId, request.branchId(), ids);
        if (current.size() != ids.size()) {
            throw new NotFoundException("LISTING_NOT_FOUND",
                    "Algún producto no está listado en esa sucursal.");
        }
        BigDecimal value = request.value();
        boolean percent = "PERCENT".equals(request.priceMode());
        if (percent ? value.compareTo(MIN_PERCENT) < 0 || value.compareTo(MAX_PERCENT) > 0
                : value.signum() <= 0) {
            throw new BadRequestException("INVALID_PRICE_VALUE", percent
                    ? "La variación debe estar entre -90% y +500%."
                    : "El precio debe ser mayor que 0.");
        }
        BigDecimal factor = BigDecimal.ONE.add(value.movePointLeft(2));
        inventoryActor.bind(email);
        for (UUID id : ids) {
            BigDecimal next = (percent ? current.get(id).multiply(factor) : value).setScale(0, RoundingMode.HALF_UP);
            if (next.signum() <= 0 || next.compareTo(MAX_PRICE) > 0) {
                throw new BadRequestException("INVALID_PRICE",
                        "El precio resultante debe estar entre 1 y 9.999.999.999 pesos.",
                        Map.of("productId", id));
            }
            stockService.updateListing(request.branchId(), id, next, null);
        }
        return new BulkProductResult(ids.size(), List.of());
    }
}
