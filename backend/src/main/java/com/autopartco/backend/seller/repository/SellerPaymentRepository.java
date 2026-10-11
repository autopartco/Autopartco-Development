package com.autopartco.backend.seller.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.seller.entity.SellerPayment;

import jakarta.persistence.LockModeType;

public interface SellerPaymentRepository extends JpaRepository<SellerPayment, UUID> {
    Optional<SellerPayment> findByReference(String reference);

    boolean existsByProviderTransactionId(String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from SellerPayment payment "
            + "join fetch payment.application application "
            + "where payment.reference = :reference")
    Optional<SellerPayment> findByReferenceForUpdate(@Param("reference") String reference);
}
