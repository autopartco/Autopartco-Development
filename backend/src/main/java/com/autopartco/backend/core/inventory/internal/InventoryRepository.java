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
}
