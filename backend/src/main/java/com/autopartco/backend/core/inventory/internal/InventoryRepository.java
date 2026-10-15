package com.autopartco.backend.core.inventory.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Stock y precio por sucursal. Las consultas de listado (que cruzan sucursal, almacén y producto) viven en
 * {@code catalog.ListingQueries}.
 */
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("select i from Inventory i where i.branchId = :branchId and i.productId = :productId")
    List<Inventory> findListings(@Param("branchId") UUID branchId, @Param("productId") UUID productId);

    @Modifying
    @Query("update Inventory i set i.deletedAt = current_timestamp where i.productId = :productId")
    void softDeleteAllForProduct(@Param("productId") UUID productId);

    @Query("select count(i) from Inventory i where i.productId = :productId")
    long countListingsOfProduct(@Param("productId") UUID productId);

    /** Borrado lógico de un listado vigente de una sucursal del almacén. Devuelve las filas afectadas (0 o 1). */
    @Modifying
    @Query(value = """
            update inventory set deleted_at = now(), updated_at = now(), version = coalesce(version, 0) + 1
            where branch_id = :branchId and product_id = :productId and deleted_at is null
              and exists (select 1 from branches b where b.id = inventory.branch_id and b.store_id = :storeId)
            """, nativeQuery = true)
    int softDeleteListing(
            @Param("storeId") UUID storeId,
            @Param("branchId") UUID branchId,
            @Param("productId") UUID productId
    );

    /**
     * Reactiva un listado borrado lógicamente: {@code uq_inventory_branch_product} no es parcial, así que no se puede
     * insertar otra fila para la misma sucursal y producto. Devuelve las filas reactivadas (0 o 1).
     */
    @Modifying
    @Query(value = """
            update inventory set deleted_at = null, price = :price, stock = :stock,
                   updated_at = now(), version = coalesce(version, 0) + 1
            where branch_id = :branchId and product_id = :productId and deleted_at is not null
            """, nativeQuery = true)
    int restoreListing(
            @Param("branchId") UUID branchId,
            @Param("productId") UUID productId,
            @Param("price") java.math.BigDecimal price,
            @Param("stock") Integer stock
    );

    /** Conteos de listados de un almacén (sucursales no borradas). */
    @Query(value = """
            select count(*) as listings,
                   count(*) filter (where i.stock <= 0) as outOfStock,
                   count(*) filter (where i.stock > 0 and i.stock <= 3) as lowStock
            from inventory i
            join branches b on b.id = i.branch_id and b.deleted_at is null
            where i.deleted_at is null and b.store_id = :storeId
            """, nativeQuery = true)
    StockCounts countStockForStore(@Param("storeId") UUID storeId);

    interface StockCounts {
        long getListings();

        long getOutOfStock();

        long getLowStock();
    }
}
