package com.autopartco.backend.core.billing.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {
    Optional<SubscriptionPayment> findByReference(String reference);

    boolean existsByProviderTransactionId(String providerTransactionId);

    // Conserva el inner join del antiguo "join fetch payment.application": solo devuelve el pago si su fila de
    // seller_applications existe y no está borrada lógicamente.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from SubscriptionPayment payment "
            + "join StoreSubscription application on application.id = payment.storeApplicationId "
            + "where payment.reference = :reference")
    Optional<SubscriptionPayment> lockByReference(@Param("reference") String reference);
}
