package com.autopartco.backend.core.identity.internal;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenAndPurpose(String token, String purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountToken token where token.token = :token and token.purpose = :purpose")
    Optional<AccountToken> findByTokenAndPurposeForUpdate(
            @Param("token") String token,
            @Param("purpose") String purpose
    );
}
