package com.autopartco.backend.core.billing.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StoreSubscriptionRepository extends JpaRepository<StoreSubscription, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from StoreSubscription subscription where subscription.id = :id")
    Optional<StoreSubscription> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from StoreSubscription subscription where subscription.storeId = :storeId")
    Optional<StoreSubscription> lockByStoreId(@Param("storeId") UUID storeId);

    @Query("select subscription from StoreSubscription subscription where subscription.storeId = :storeId")
    Optional<StoreSubscription> findByStoreId(@Param("storeId") UUID storeId);
}
