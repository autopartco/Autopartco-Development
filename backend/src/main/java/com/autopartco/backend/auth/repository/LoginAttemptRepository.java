package com.autopartco.backend.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.auth.entity.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    List<LoginAttempt> findByAccountId(UUID accountId, Pageable pageable);
}
