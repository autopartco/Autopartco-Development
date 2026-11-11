package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;


public interface AccountSessionRepository extends JpaRepository<AccountSession, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO account_sessions (
                id,
                account_id,
                refresh_token_hash,
                ip_address,
                user_agent,
                expired_at,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :sessionId,
                :accountId,
                :refreshTokenHash,
                CAST(:ipAddress AS inet),
                :userAgent,
                :expiredAt,
                now(),
                now(),
                0
            )
            """, nativeQuery = true)
    int insertSession(
            @Param("sessionId") UUID sessionId,
            @Param("accountId") UUID accountId,
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("expiredAt") LocalDateTime expiredAt
    );

    @Query("""
            SELECT session
            FROM AccountSession session
            WHERE session.account.id = :accountId
              AND session.revokedAt IS NULL
            ORDER BY session.createdAt DESC
            """)
    List<AccountSession> findNonRevokedByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT session
            FROM AccountSession session
            WHERE session.account.id = :accountId
              AND session.revokedAt IS NULL
              AND session.expiredAt > :currentTime
            ORDER BY session.createdAt DESC
            """)
    List<AccountSession> findActiveByAccountId(
            @Param("accountId") UUID accountId,
            @Param("currentTime") LocalDateTime currentTime
    );

    /** User-agents distintos de todas las sesiones de la cuenta (vigentes, vencidas o revocadas). */
    @Query("SELECT DISTINCT session.userAgent FROM AccountSession session WHERE session.account.id = :accountId")
    List<String> findDistinctUserAgentsByAccountId(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM AccountSession session WHERE session.refreshTokenHash = :tokenHash")
    Optional<AccountSession> lockByRefreshTokenHash(@Param("tokenHash") String tokenHash);
}
