package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.core.organization.StoreApplicationStatus;

import jakarta.persistence.LockModeType;

public interface StoreApplicationRepository extends JpaRepository<StoreApplication, UUID> {
    @Query("""
            select distinct application from StoreApplication application
            join fetch application.store store
            where application.status = :status
            order by application.createdAt
            """)
    List<StoreApplication> findAllByStatusWithOwner(
            @Param("status") StoreApplicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from StoreApplication application where application.id = :id")
    Optional<StoreApplication> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from StoreApplication application "
            + "where application.onboardingTokenHash = :tokenHash")
    Optional<StoreApplication> findByOnboardingTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("select application from StoreApplication application "
            + "join fetch application.store where application.store.id = :storeId")
    Optional<StoreApplication> findByStoreId(@Param("storeId") UUID storeId);

    boolean existsByStore_Id(UUID storeId);

    boolean existsByStore_IdAndStatus(UUID storeId, StoreApplicationStatus status);

    boolean existsByApplicantAccountIdAndStatusIn(UUID applicantAccountId, Collection<StoreApplicationStatus> statuses);

    @Query("select application from StoreApplication application "
            + "join fetch application.store store "
            + "where application.applicantAccountId = :applicantAccountId "
            + "order by application.createdAt desc")
    List<StoreApplication> findByApplicant(@Param("applicantAccountId") UUID applicantAccountId);
}
