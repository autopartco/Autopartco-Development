package com.autopartco.backend.core.pricing.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.core.pricing.PromotionScope;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findByScopeAndStoreIdOrderByCreatedAtDesc(PromotionScope scope, UUID storeId);

    List<Promotion> findByScopeAndStoreIdIsNullOrderByCreatedAtDesc(PromotionScope scope);

    Optional<Promotion> findByIdAndScopeAndStoreId(UUID id, PromotionScope scope, UUID storeId);

    Optional<Promotion> findByIdAndScopeAndStoreIdIsNull(UUID id, PromotionScope scope);

    @Query("""
            select p from Promotion p
            where p.enabled = true
              and p.startsAt <= :at
              and p.endsAt > :at
            """)
    List<Promotion> findActiveAt(@Param("at") OffsetDateTime at);
}
