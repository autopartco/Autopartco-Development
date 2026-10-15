package com.autopartco.backend.core.inventory;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.internal.Inventory;
import com.autopartco.backend.core.inventory.internal.InventoryRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Stock y precio base de un producto en una sucursal. No valida la propiedad de la sucursal ni del producto: lo
 * hace {@code catalog.ProductCatalog} antes de llamar.
 */
@Service
@AllArgsConstructor
public class StockService {
    private final InventoryRepository inventoryRepository;

    @Transactional
    public void createListing(UUID branchId, UUID productId, Integer stock, BigDecimal price) {
        inventoryRepository.save(Inventory.builder()
                .branchId(branchId)
                .productId(productId)
                .stock(stock)
                .price(price)
                .build());
    }

    /** Actualiza precio y stock (los nulos no cambian). Sin listado: {@code PRODUCT_NOT_FOUND}. */
    @Transactional
    public void updateListing(UUID branchId, UUID productId, BigDecimal price, Integer stock) {
        Inventory inventory = DataAccessUtils.singleResult(inventoryRepository.findListings(branchId, productId));
        if (inventory == null) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto.");
        }
        if (price != null) inventory.setPrice(price);
        if (stock != null) inventory.setStock(stock);
    }

    /** Borrado lógico de todos los listados del producto. */
    @Transactional
    public void removeAllForProduct(UUID productId) {
        inventoryRepository.softDeleteAllForProduct(productId);
    }

    /** Agrega el producto a una sucursal; si ya está listado, {@code LISTING_ALREADY_EXISTS}. */
    @Transactional
    public void addListing(UUID branchId, UUID productId, Integer stock, BigDecimal price) {
        if (!inventoryRepository.findListings(branchId, productId).isEmpty()) {
            throw new ConflictException("LISTING_ALREADY_EXISTS", "El producto ya está listado en esa sucursal.");
        }
        if (inventoryRepository.restoreListing(branchId, productId, price, stock) == 0) {
            createListing(branchId, productId, stock, price);
        }
    }

    /** Borrado lógico del listado del producto en la sucursal; sin listado, {@code LISTING_NOT_FOUND}. */
    @Transactional
    public void removeListing(UUID storeId, UUID branchId, UUID productId) {
        if (inventoryRepository.softDeleteListing(storeId, branchId, productId) == 0) {
            throw new NotFoundException("LISTING_NOT_FOUND", "El producto no está listado en esa sucursal.");
        }
    }

    @Transactional(readOnly = true)
    public long countListingsOfProduct(UUID productId) {
        return inventoryRepository.countListingsOfProduct(productId);
    }

    /** Conteos de inventario del almacén: listados, agotados (stock ≤ 0) y con poco stock (0 &lt; stock ≤ 3). */
    @Transactional(readOnly = true)
    public StockSummary summaryForStore(UUID storeId) {
        InventoryRepository.StockCounts counts = inventoryRepository.countStockForStore(storeId);
        return new StockSummary(counts.getListings(), counts.getOutOfStock(), counts.getLowStock());
    }

    public record StockSummary(long listings, long outOfStock, long lowStock) {
    }
}
