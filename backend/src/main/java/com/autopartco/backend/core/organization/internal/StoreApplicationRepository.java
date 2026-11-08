package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Historial para root ({@code status} nulo = todas): las de {@code pendingStatus} primero, de la más antigua a la
     * más reciente; el resto por {@code reviewedAt} desc (nulos al final) y {@code createdAt} desc.
     */
    @Query(value = """
            select application from StoreApplication application
            join fetch application.store store
            where (:status is null or application.status = :status)
            order by case when application.status = :pendingStatus then 0 else 1 end,
                     case when application.status = :pendingStatus then application.createdAt end asc,
                     application.reviewedAt desc nulls last,
                     application.createdAt desc,
                     application.id
            """,
            countQuery = """
            select count(application) from StoreApplication application
            where (:status is null or application.status = :status)
            """)
    Page<StoreApplication> findHistory(
            @Param("status") StoreApplicationStatus status,
            @Param("pendingStatus") StoreApplicationStatus pendingStatus,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from StoreApplication application where application.id = :id")
    Optional<StoreApplication> lockById(@Param("id") UUID id);

    @Query("select application from StoreApplication application "
            + "join fetch application.store where application.store.id = :storeId")
    Optional<StoreApplication> findByStoreId(@Param("storeId") UUID storeId);

    @Query("select case when count(application) > 0 then true else false end "
            + "from StoreApplication application where application.store.id = :storeId")
    boolean existsForStore(@Param("storeId") UUID storeId);

    /** La literal de enum en JPQL debe ir calificada por especificación (JPA 4.6.1). */
    @Query("select case when count(application) > 0 then true else false end "
            + "from StoreApplication application where application.store.id = :storeId "
            + "and application.status = com.autopartco.backend.core.organization.StoreApplicationStatus.ACTIVE")
    boolean isActive(@Param("storeId") UUID storeId);

    /** Estado de la solicitud por tienda: filas {@code [storeId, status]}. Las tiendas sin solicitud no aparecen. */
    @Query("select application.store.id, application.status from StoreApplication application "
            + "where application.store.id in :storeIds")
    List<Object[]> findStatusesByStoreIds(@Param("storeIds") Collection<UUID> storeIds);

    boolean existsByApplicantAccountIdAndStatusIn(UUID applicantAccountId, Collection<StoreApplicationStatus> statuses);

    @Query("select application from StoreApplication application "
            + "join fetch application.store store "
            + "where application.applicantAccountId = :applicantAccountId "
            + "order by application.createdAt desc")
    List<StoreApplication> findByApplicant(@Param("applicantAccountId") UUID applicantAccountId);
}
