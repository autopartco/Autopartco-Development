package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;


public interface StorePermissionRepository extends JpaRepository<StorePermission, UUID> {
    List<StorePermission> findAllByOrderByCodeAsc();

    List<StorePermission> findByCodeIn(Collection<String> codes);
}
