package com.autopartco.backend.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.product.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("""
            select distinct i from Inventory i
            join fetch i.branch b
            join fetch i.product p
            join fetch p.manufacturer
            join fetch p.category
            join fetch p.state
            left join fetch p.compatibilities
            where p.store.id = :storeId
            order by p.name, b.name
            """)
    List<Inventory> findAllInStore(@Param("storeId") UUID storeId);

    @Query("""
            select distinct i from Inventory i
            join fetch i.branch b
            join fetch i.product p
            join fetch p.manufacturer
            join fetch p.category
            join fetch p.state
            left join fetch p.compatibilities
            where p.id = :productId
              and p.store.id = :storeId
              and b.id = :branchId
            """)
    Optional<Inventory> findInStore(
            @Param("storeId") UUID storeId,
            @Param("branchId") UUID branchId,
            @Param("productId") UUID productId
    );

    @Query("""
            select distinct i from Inventory i
            join fetch i.branch b
            join fetch b.store s
            join fetch i.product p
            join fetch p.manufacturer
            join fetch p.category
            join fetch p.state
            left join fetch p.compatibilities
            where p.state.code = 'ACTIVE'
              and s.state.code = 'ACTIVE'
              and b.state.code = 'ACTIVE'
              and i.stock > 0
            order by p.name, s.businessName, b.name
            """)
    List<Inventory> findPublished();

    @Query("""
            select distinct i from Inventory i
            join fetch i.branch b
            join fetch b.store s
            join fetch i.product p
            join fetch p.manufacturer
            join fetch p.category
            join fetch p.state
            left join fetch p.compatibilities
            where p.id = :productId
              and p.state.code = 'ACTIVE'
              and s.state.code = 'ACTIVE'
              and b.state.code = 'ACTIVE'
              and i.stock > 0
            order by i.price asc, s.businessName, b.name
            """)
    List<Inventory> findPublishedByProductId(@Param("productId") UUID productId);

    @Modifying
    @Query("update Inventory i set i.deletedAt = current_timestamp where i.product.id = :productId")
    void softDeleteAllForProduct(@Param("productId") UUID productId);
}