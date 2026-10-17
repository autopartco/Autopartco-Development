package com.autopartco.backend.core.catalog.internal;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reglas de vehículo vigentes ({@code @SQLRestriction} excluye las borradas lógicamente). */
public interface ProductVehicleRuleRepository extends JpaRepository<ProductVehicleRule, UUID> {

    @Query("select r from ProductVehicleRule r where r.productId in :productIds")
    List<ProductVehicleRule> findAllOfProducts(@Param("productIds") Collection<UUID> productIds);

    @Query("select r from ProductVehicleRule r where r.productId = :productId")
    List<ProductVehicleRule> findAllOfProduct(@Param("productId") UUID productId);

    /** Borrado lógico de reglas concretas del producto (acotado por {@code productId}). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update ProductVehicleRule r set r.deletedAt = :now
            where r.productId = :productId and r.id in :ids
            """)
    int softDelete(@Param("productId") UUID productId, @Param("ids") Collection<UUID> ids,
            @Param("now") LocalDateTime now);
}
