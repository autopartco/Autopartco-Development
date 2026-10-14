package com.autopartco.backend.core.pricing.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las literales de enum en JPQL deben ir calificadas por especificación (JPA 4.6.1). */
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    @Query("""
            select p from Promotion p
            where p.scope = com.autopartco.backend.core.pricing.PromotionScope.STORE
              and p.storeId = :storeId
            order by p.createdAt desc
            """)
    List<Promotion> findStorePromotions(@Param("storeId") UUID storeId);

    @Query("""
            select p from Promotion p
            where p.scope = com.autopartco.backend.core.pricing.PromotionScope.PLATFORM
              and p.storeId is null
            order by p.createdAt desc
            """)
    List<Promotion> findPlatformPromotions();

    @Query("""
            select p from Promotion p
            where p.id = :id
              and p.scope = com.autopartco.backend.core.pricing.PromotionScope.STORE
              and p.storeId = :storeId
            """)
    Optional<Promotion> findStorePromotion(@Param("storeId") UUID storeId, @Param("id") UUID id);

    @Query("""
            select p from Promotion p
            where p.id = :id
              and p.scope = com.autopartco.backend.core.pricing.PromotionScope.PLATFORM
              and p.storeId is null
            """)
    Optional<Promotion> findPlatformPromotion(@Param("id") UUID id);

    @Query("""
            select p from Promotion p
            where p.enabled = true
              and p.startsAt <= :at
              and p.endsAt > :at
            """)
    List<Promotion> findActiveAt(@Param("at") OffsetDateTime at);
}
