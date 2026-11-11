package com.autopartco.backend.core.advertising.internal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

    /** Pagos {@code PENDING} de un proveedor y entorno creados antes de {@code before}, los más viejos primero. */
    @Query("""
            select payment from AdPayment payment
            where payment.status = 'PENDING' and payment.provider = :provider
              and payment.environment = :environment and payment.createdAt < :before
            order by payment.createdAt
            """)
    List<AdPayment> findPendingCreatedBefore(@Param("provider") String provider,
            @Param("environment") String environment, @Param("before") LocalDateTime before, Pageable pageable);
}
