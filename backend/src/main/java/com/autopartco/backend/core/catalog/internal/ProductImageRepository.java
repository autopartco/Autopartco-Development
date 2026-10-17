package com.autopartco.backend.core.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("""
            select i from ProductImage i
            where i.product.id = :productId
            order by i.displayOrder asc, i.createdAt asc
            """)
    List<ProductImage> findAllOfProduct(@Param("productId") UUID productId);

    @Query("""
            select i from ProductImage i
            where i.product.id in :productIds
            order by i.product.id asc, i.displayOrder asc, i.createdAt asc
            """)
    List<ProductImage> findAllOfProducts(@Param("productIds") Collection<UUID> productIds);

    Optional<ProductImage> findByIdAndProduct_Id(UUID imageId, UUID productId);

    long countByProduct_Id(UUID productId);

    @Query("select coalesce(max(i.displayOrder), -1) from ProductImage i where i.product.id = :productId")
    int findMaxDisplayOrder(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductImage i set i.isMain = false where i.product.id = :productId")
    void clearMainImage(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductImage i set i.isMain = true where i.id = :imageId and i.product.id = :productId")
    void markMain(@Param("productId") UUID productId, @Param("imageId") UUID imageId);

    @Modifying
    @Query("delete from ProductImage i where i.product.id = :productId")
    void deleteAllForProduct(@Param("productId") UUID productId);
}
