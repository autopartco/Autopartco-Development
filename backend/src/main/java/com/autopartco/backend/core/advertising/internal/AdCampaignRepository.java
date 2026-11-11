package com.autopartco.backend.core.advertising.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.core.advertising.AdCampaignStatus;

import jakarta.persistence.LockModeType;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, UUID> {

    Optional<AdCampaign> findByIdAndStoreId(UUID id, UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from AdCampaign campaign where campaign.id = :id and campaign.storeId = :storeId")
    Optional<AdCampaign> lockByIdAndStoreId(@Param("id") UUID id, @Param("storeId") UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from AdCampaign campaign where campaign.id = :id")
    Optional<AdCampaign> lockById(@Param("id") UUID id);

    List<AdCampaign> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<AdCampaign> findByStoreIdAndStatusOrderByCreatedAtDesc(UUID storeId, AdCampaignStatus status);

    List<AdCampaign> findByStatusInOrderByCreatedAtDesc(Collection<AdCampaignStatus> statuses, Pageable pageable);

    List<AdCampaign> findByStoreIdAndStatusInOrderByCreatedAtDesc(UUID storeId,
            Collection<AdCampaignStatus> statuses, Pageable pageable);
}
