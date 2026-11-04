package com.autopartco.backend.core.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select count(product) from Product product where product.storeId = :storeId")
    long countProductsInStore(@Param("storeId") UUID storeId);

    @Query("select p from Product p where p.id = :id and p.storeId = :storeId")
    Optional<Product> findOwned(@Param("storeId") UUID storeId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.storeId = :storeId")
    Optional<Product> lockOwned(@Param("storeId") UUID storeId, @Param("id") UUID id);

    /**
     * Carga los productos de un listado con su estado y su fabricante (sin N+1). Join interno con el fabricante: igual
     * que las consultas de listado, que exigen fabricante no borrado.
     */
    @Query("select p from Product p join fetch p.state join fetch p.manufacturer where p.id in :ids")
    List<Product> findAllWithDetails(@Param("ids") Collection<UUID> ids);
}
