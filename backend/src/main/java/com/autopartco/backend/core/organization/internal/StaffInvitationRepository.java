package com.autopartco.backend.core.organization.internal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import jakarta.persistence.LockModeType;

public interface StaffInvitationRepository
        extends JpaRepository<StaffInvitation, UUID> {
    Optional<StaffInvitation> findByStore_IdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID storeId,
            String email
    );

    List<StaffInvitation> findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
            UUID storeId
    );

    long countByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
            UUID storeId,
            LocalDateTime now
    );

    Optional<StaffInvitation> findByIdAndStore_Id(UUID id, UUID storeId);

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM StaffInvitation invitation
            WHERE invitation.tokenHash = :tokenHash
            """)
    Optional<StaffInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}
