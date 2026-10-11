package com.autopartco.backend.seller.repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;

import jakarta.persistence.LockModeType;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {
    @Query("""
            select distinct application from SellerApplication application
            join fetch application.store store
            join fetch application.plan plan
            join fetch application.selectedPlan selectedPlan
            join fetch application.applicant applicant
            join fetch applicant.person
            where application.status = :status
            order by application.createdAt
            """)
    List<SellerApplication> findAllByStatusWithOwner(
            @Param("status") SellerApplicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from SellerApplication application where application.id = :id")
    Optional<SellerApplication> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from SellerApplication application "
            + "where application.onboardingTokenHash = :tokenHash")
    Optional<SellerApplication> findByOnboardingTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("select application from SellerApplication application "
            + "join fetch application.store where application.store.id = :storeId")
    Optional<SellerApplication> findByStoreId(@Param("storeId") UUID storeId);

    boolean existsByStore_Id(UUID storeId);

    boolean existsByStore_IdAndStatus(UUID storeId, SellerApplicationStatus status);

    boolean existsByApplicant_IdAndStatusIn(UUID applicantId, Collection<SellerApplicationStatus> statuses);

    @Query("select application from SellerApplication application "
            + "join fetch application.store store "
            + "join fetch application.plan plan "
            + "join fetch application.selectedPlan selectedPlan "
            + "where lower(application.applicant.email) = lower(:email) "
            + "order by application.createdAt desc")
    List<SellerApplication> findByApplicantEmail(@Param("email") String email);
}
