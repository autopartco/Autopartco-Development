package com.autopartco.backend.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.product.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);

    List<ProductImage> findByProduct_IdInOrderByProduct_IdAscDisplayOrderAscCreatedAtAsc(
            Collection<UUID> productIds
    );

    Optional<ProductImage> findByIdAndProduct_Id(UUID imageId, UUID productId);

    long countByProduct_Id(UUID productId);

    @Query("select coalesce(max(i.displayOrder), -1) from ProductImage i where i.product.id = :productId")
    int findMaxDisplayOrder(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductImage i set i.isMain = false where i.product.id = :productId")
    void clearMainImage(@Param("productId") UUID productId);

    @Modifying
    @Query("delete from ProductImage i where i.product.id = :productId")
    void deleteAllForProduct(@Param("productId") UUID productId);
}
