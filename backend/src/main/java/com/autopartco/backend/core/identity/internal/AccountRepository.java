package com.autopartco.backend.core.identity.internal;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;


public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<Account> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM Account account WHERE lower(account.email) = lower(:email)")
    Optional<Account> findByEmailIgnoreCaseForUpdate(@Param("email") String email);
}
