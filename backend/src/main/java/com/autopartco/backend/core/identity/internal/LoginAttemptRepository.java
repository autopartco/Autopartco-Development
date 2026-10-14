package com.autopartco.backend.core.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    List<LoginAttempt> findByAccountId(UUID accountId, Pageable pageable);
}
