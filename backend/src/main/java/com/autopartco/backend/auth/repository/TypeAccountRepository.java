package com.autopartco.backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.auth.entity.AccountType;

public interface TypeAccountRepository extends JpaRepository<AccountType, UUID> {
    Optional<AccountType> findByCode(String code);
}
