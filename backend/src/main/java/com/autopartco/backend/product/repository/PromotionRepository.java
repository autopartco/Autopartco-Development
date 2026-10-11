package com.autopartco.backend.product.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.product.entity.Promotion;
import com.autopartco.backend.product.entity.PromotionScope;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findByScopeAndStore_IdOrderByCreatedAtDesc(PromotionScope scope, UUID storeId);

    List<Promotion> findByScopeAndStoreIsNullOrderByCreatedAtDesc(PromotionScope scope);

    Optional<Promotion> findByIdAndScopeAndStore_Id(UUID id, PromotionScope scope, UUID storeId);

    Optional<Promotion> findByIdAndScopeAndStoreIsNull(UUID id, PromotionScope scope);

    @Query("""
            select p from Promotion p
            left join fetch p.store
            where p.enabled = true
              and p.startsAt <= :at
              and p.endsAt > :at
            """)
    List<Promotion> findActiveAt(@Param("at") OffsetDateTime at);
}
