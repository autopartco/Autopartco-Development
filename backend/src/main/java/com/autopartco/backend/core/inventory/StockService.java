package com.autopartco.backend.core.inventory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.inventory.internal.Inventory;
import com.autopartco.backend.core.inventory.internal.InventoryRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Stock y precio base de un producto en una sucursal. No valida la propiedad de la sucursal ni del producto: lo
 * hace {@code catalog.ProductCatalog} antes de llamar.
 */
@Service
@AllArgsConstructor
public class StockService {
    /**
     * Descuento atómico: solo afecta al listado vivo con stock suficiente y bloquea la fila hasta el commit. Sube
     * {@code version} para mantener coherente el bloqueo optimista de la entidad {@code Inventory}.
     */
    static final String DECREMENT_SQL = """
            UPDATE inventory
            SET stock = stock - :units, version = version + 1, updated_at = now()
            WHERE branch_id = :branchId AND product_id = :productId
              AND deleted_at IS NULL AND stock >= :units
            RETURNING id, stock, price
            """;

    static final String AVAILABLE_STOCK_SQL = """
            SELECT stock
            FROM inventory
            WHERE branch_id = :branchId AND product_id = :productId
              AND deleted_at IS NULL
            """;

    private final InventoryRepository inventoryRepository;
    private final JdbcClient jdbcClient;

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

    /**
     * {@code UPDATE} condicional atómico. Vacío si no hay listado vivo o si {@code stock < units}. Debe correr dentro
     * de la transacción del llamador (propagación por defecto) para que una excepción posterior revierta el descuento.
     */
    @Transactional
    public Optional<StockDecrement> decrementIfAvailable(UUID branchId, UUID productId, int units) {
        if (units < 1) {
            throw new IllegalArgumentException("units must be >= 1");
        }
        return jdbcClient.sql(DECREMENT_SQL)
                .param("units", units)
                .param("branchId", branchId)
                .param("productId", productId)
                .query((result, row) -> new StockDecrement(
                        result.getObject("id", UUID.class),
                        result.getInt("stock"),
                        result.getBigDecimal("price")))
                .list()
                .stream()
                .findFirst();
    }

    /** Stock actual del listado vivo, o 0 si no existe. Solo para los detalles de error. */
    @Transactional(readOnly = true)
    public int availableStock(UUID branchId, UUID productId) {
        return jdbcClient.sql(AVAILABLE_STOCK_SQL)
                .param("branchId", branchId)
                .param("productId", productId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }
}
