package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface BranchRepository extends JpaRepository<Branch, UUID> {

    @Query("select count(b) from Branch b where b.store.id = :storeId and b.state.code = 'ACTIVE'")
    long countActiveInStore(@Param("storeId") UUID storeId);

    @Query("select b from Branch b where b.store.id = :storeId order by b.name")
    List<Branch> findAllInStore(@Param("storeId") UUID storeId);

    @Query("select b from Branch b where b.id = :branchId and b.store.id = :storeId")
    Optional<Branch> findInStore(@Param("branchId") UUID branchId, @Param("storeId") UUID storeId);

    @Query("select case when count(b) > 0 then true else false end from Branch b "
            + "where b.store.id = :storeId and lower(b.name) = lower(:name)")
    boolean nameExists(@Param("storeId") UUID storeId, @Param("name") String name);

    @Query("select case when count(b) > 0 then true else false end from Branch b "
            + "where b.store.id = :storeId and lower(b.name) = lower(:name) and b.id <> :branchId")
    boolean nameUsedByOther(
            @Param("storeId") UUID storeId,
            @Param("name") String name,
            @Param("branchId") UUID branchId
    );
}