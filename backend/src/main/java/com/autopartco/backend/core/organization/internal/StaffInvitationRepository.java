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
    @Query("""
            SELECT invitation
            FROM StaffInvitation invitation
            WHERE invitation.store.id = :storeId
              AND upper(invitation.email) = upper(:email)
              AND invitation.acceptedAt IS NULL
              AND invitation.revokedAt IS NULL
            """)
    Optional<StaffInvitation> findPendingByEmail(
            @Param("storeId") UUID storeId,
            @Param("email") String email
    );

    @Query("""
            SELECT invitation
            FROM StaffInvitation invitation
            WHERE invitation.store.id = :storeId
              AND invitation.acceptedAt IS NULL
              AND invitation.revokedAt IS NULL
            ORDER BY invitation.createdAt DESC
            """)
    List<StaffInvitation> findPendingInStore(@Param("storeId") UUID storeId);

    @Query("""
            SELECT count(invitation)
            FROM StaffInvitation invitation
            WHERE invitation.store.id = :storeId
              AND invitation.acceptedAt IS NULL
              AND invitation.revokedAt IS NULL
              AND invitation.expiresAt > :now
            """)
    long countPendingInStore(
            @Param("storeId") UUID storeId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT invitation
            FROM StaffInvitation invitation
            WHERE invitation.store.id = :storeId
              AND invitation.id = :id
            """)
    Optional<StaffInvitation> findInStore(
            @Param("storeId") UUID storeId,
            @Param("id") UUID id
    );

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM StaffInvitation invitation
            WHERE invitation.tokenHash = :tokenHash
            """)
    Optional<StaffInvitation> lockByTokenHash(
            @Param("tokenHash") String tokenHash
    );
}
