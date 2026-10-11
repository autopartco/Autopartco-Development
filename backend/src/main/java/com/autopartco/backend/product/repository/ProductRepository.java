package com.autopartco.backend.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.product.entity.Product;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select count(product) from Product product where product.store.id = :storeId")
    long countProductsInStore(@Param("storeId") UUID storeId);

    @Query("select p from Product p where p.id = :id and p.store.id = :storeId")
    Optional<Product> findOwned(@Param("storeId") UUID storeId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.store.id = :storeId")
    Optional<Product> findOwnedForUpdate(@Param("storeId") UUID storeId, @Param("id") UUID id);
}