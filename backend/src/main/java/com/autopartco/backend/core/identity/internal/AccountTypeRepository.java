package com.autopartco.backend.core.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface AccountTypeRepository extends JpaRepository<AccountType, UUID> {
    Optional<AccountType> findByCode(String code);
}
