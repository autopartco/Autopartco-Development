package com.autopartco.backend.core.advertising.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AdPaymentRepository extends JpaRepository<AdPayment, UUID> {

    Optional<AdPayment> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from AdPayment payment where payment.reference = :reference")
    Optional<AdPayment> lockByReference(@Param("reference") String reference);

    boolean existsByProviderTransactionId(String providerTransactionId);

    List<AdPayment> findByCampaignIdAndStatus(UUID campaignId, String status);

    List<AdPayment> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);
}
