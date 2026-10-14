package com.autopartco.backend.core.organization.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;


public interface StoreRoleRepository extends JpaRepository<StoreRole, UUID> {
    Optional<StoreRole> findByStore_IdAndCode(UUID storeId, String code);
}
