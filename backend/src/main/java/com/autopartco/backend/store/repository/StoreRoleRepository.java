package com.autopartco.backend.store.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.store.entity.StoreRole;

public interface StoreRoleRepository extends JpaRepository<StoreRole, UUID> {
    Optional<StoreRole> findByStore_IdAndCode(UUID storeId, String code);
}
