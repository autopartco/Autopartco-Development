package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenAndPurpose(String token, String purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountToken token where token.token = :token and token.purpose = :purpose")
    Optional<AccountToken> lockByTokenAndPurpose(
            @Param("token") String token,
            @Param("purpose") String purpose
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountToken token where token.id = :id and token.purpose = :purpose")
    Optional<AccountToken> lockByIdAndPurpose(@Param("id") UUID id, @Param("purpose") String purpose);

    /** Códigos vigentes (sin usar ni vencer) de la cuenta, el más reciente primero. */
    @Query("""
            select token from AccountToken token
            where token.account.id = :accountId and token.purpose = :purpose
              and token.used = false and token.expirationAt > :now
            order by token.createdAt desc
            """)
    List<AccountToken> findActive(
            @Param("accountId") UUID accountId,
            @Param("purpose") String purpose,
            @Param("now") LocalDateTime now
    );

    Optional<AccountToken> findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(UUID accountId, String purpose);

    long countByAccount_IdAndPurposeAndCreatedAtAfter(UUID accountId, String purpose, LocalDateTime since);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AccountToken token set token.used = true, token.updatedAt = :now
            where token.account.id = :accountId and token.purpose in :purposes and token.used = false
            """)
    int markUsed(
            @Param("accountId") UUID accountId,
            @Param("purposes") Collection<String> purposes,
            @Param("now") LocalDateTime now
    );
}
