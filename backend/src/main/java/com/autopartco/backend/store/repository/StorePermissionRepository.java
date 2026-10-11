package com.autopartco.backend.store.repository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.store.entity.StorePermission;

public interface StorePermissionRepository extends JpaRepository<StorePermission, UUID> {
    List<StorePermission> findAllByOrderByCodeAsc();

    List<StorePermission> findByCodeIn(Collection<String> codes);
}
