package com.autopartco.backend.seller.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.seller.entity.SellerCollaboratorInvitation;

import jakarta.persistence.LockModeType;

public interface SellerCollaboratorInvitationRepository
        extends JpaRepository<SellerCollaboratorInvitation, UUID> {
    Optional<SellerCollaboratorInvitation> findByStore_IdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID storeId,
            String email
    );

    List<SellerCollaboratorInvitation> findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
            UUID storeId
    );

    long countByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
            UUID storeId,
            LocalDateTime now
    );

    Optional<SellerCollaboratorInvitation> findByIdAndStore_Id(UUID id, UUID storeId);

    Optional<SellerCollaboratorInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM SellerCollaboratorInvitation invitation
            WHERE invitation.tokenHash = :tokenHash
            """)
    Optional<SellerCollaboratorInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}
