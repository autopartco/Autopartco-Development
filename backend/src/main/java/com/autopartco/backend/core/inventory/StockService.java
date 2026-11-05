package com.autopartco.backend.core.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    /**
     * Bloquea los listados que reciben unidades en el mismo orden que el descuento del checkout
     * ({@code branch_id, product_id}) para no interbloquearse con una compra en curso.
     */
    static final String LOCK_FOR_RETURN_SQL = """
            SELECT id
            FROM inventory
            WHERE id IN (:inventoryIds)
            ORDER BY branch_id, product_id
            FOR UPDATE
            """;

    static final String RETURN_SQL = """
            UPDATE inventory
            SET stock = stock + :units, version = version + 1, updated_at = now()
            WHERE id = :inventoryId
            """;

    private final InventoryRepository inventoryRepository;
    private final JdbcClient jdbcClient;
    private final StockLedger stockLedger;

    private void recordStock(UUID inventoryId, Integer stock, String reasonCode) {
        if (stock == null) {
            return;
        }
        stockLedger.apply(null, StockLedger.SOURCE_USER,
                List.of(StockLedger.StockChange.of(inventoryId, stock, reasonCode, null)));
    }

    /**
     * Crea el listado con stock 0 y registra la carga inicial en el ledger ({@code INITIAL_LOAD}) si
     * {@code stock > 0}. El actor es el ligado a la transacción ({@link StockLedger#bindActor}).
     */
    @Transactional
    public void createListing(UUID branchId, UUID productId, Integer stock, BigDecimal price) {
        Inventory saved = inventoryRepository.saveAndFlush(Inventory.builder()
                .branchId(branchId)
                .productId(productId)
                .stock(0)
                .price(price)
                .build());
        recordStock(saved.getId(), stock, StockLedger.REASON_INITIAL_LOAD);
    }

    /**
     * Actualiza precio y stock (los nulos no cambian). Sin listado: {@code PRODUCT_NOT_FOUND}. El precio se escribe
     * antes que el stock para que el trigger puente registre su historial; el stock pasa por el ledger
     * ({@code MANUAL_CORRECTION}; sin movimiento si no cambia).
     */
    @Transactional
    public void updateListing(UUID branchId, UUID productId, BigDecimal price, Integer stock) {
        Inventory inventory = DataAccessUtils.singleResult(inventoryRepository.findListings(branchId, productId));
        if (inventory == null) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto.");
        }
        // Sin tocar la entidad: el ledger cambia stock y version por JDBC y un flush de la entidad los pisaría.
        if (price != null) inventoryRepository.updatePrice(inventory.getId(), price);
        if (stock != null) recordStock(inventory.getId(), stock, StockLedger.REASON_MANUAL_CORRECTION);
    }

    /**
     * Cambia solo el precio de un listado vivo por su id (importaciones y acciones masivas). Sin listado vivo:
     * {@code LISTING_NOT_FOUND}. El trigger puente registra el historial con el actor ligado a la transacción.
     */
    @Transactional
    public void setPrice(UUID inventoryId, BigDecimal price) {
        if (inventoryRepository.updatePrice(inventoryId, price) == 0) {
            throw new NotFoundException("LISTING_NOT_FOUND", "El producto no está listado en esa sucursal.");
        }
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

    /**
     * Devuelve unidades reservadas a sus listados (también si el listado se borró después). No escribe
     * {@code inventory_movements}: el descuento del checkout tampoco lo hace (plan Wompi, N14). Corre en la
     * transacción del llamador.
     */
    @Transactional
    public void returnStock(List<StockReturn> returns) {
        if (returns == null || returns.isEmpty()) {
            return;
        }
        List<UUID> locked = jdbcClient.sql(LOCK_FOR_RETURN_SQL)
                .param("inventoryIds", returns.stream().map(StockReturn::inventoryId).distinct().toList())
                .query((result, row) -> result.getObject("id", UUID.class))
                .list();
        for (UUID inventoryId : locked) {
            int units = returns.stream()
                    .filter(item -> item.inventoryId().equals(inventoryId))
                    .mapToInt(StockReturn::units)
                    .sum();
            jdbcClient.sql(RETURN_SQL)
                    .param("units", units)
                    .param("inventoryId", inventoryId)
                    .update();
        }
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

    /**
     * Agrega el producto a una sucursal; si ya está listado, {@code LISTING_ALREADY_EXISTS}. Un listado borrado se
     * reactiva y su stock se fija por el ledger ({@code MANUAL_CORRECTION}); uno nuevo se crea como en
     * {@link #createListing}.
     */
    @Transactional
    public void addListing(UUID branchId, UUID productId, Integer stock, BigDecimal price) {
        if (!inventoryRepository.findListings(branchId, productId).isEmpty()) {
            throw new ConflictException("LISTING_ALREADY_EXISTS", "El producto ya está listado en esa sucursal.");
        }
        if (inventoryRepository.restoreListing(branchId, productId, price) == 0) {
            createListing(branchId, productId, stock, price);
            return;
        }
        Inventory restored = DataAccessUtils.singleResult(inventoryRepository.findListings(branchId, productId));
        if (restored == null) {
            throw new NotFoundException("LISTING_NOT_FOUND", "El producto no está listado en esa sucursal.");
        }
        recordStock(restored.getId(), stock, StockLedger.REASON_MANUAL_CORRECTION);
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

    /**
     * Conteos de inventario del almacén: listados, agotados (stock ≤ 0) y con poco stock (0 &lt; stock ≤ punto de
     * reorden vigente, o 3 sin política).
     */
    @Transactional(readOnly = true)
    public StockSummary summaryForStore(UUID storeId) {
        InventoryRepository.StockCounts counts = inventoryRepository.countStockForStore(storeId);
        return new StockSummary(counts.getListings(), counts.getOutOfStock(), counts.getLowStock());
    }

    public record StockSummary(long listings, long outOfStock, long lowStock) {
    }
}
