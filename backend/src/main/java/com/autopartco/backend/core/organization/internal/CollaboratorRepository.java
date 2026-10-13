package com.autopartco.backend.core.organization.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface CollaboratorRepository extends JpaRepository<Collaborator, UUID> {

    long countByStore_IdAndState_Code(UUID storeId, String stateCode);

    boolean existsByPersonIdAndStore_Id(UUID personId, UUID storeId);

    java.util.Optional<Collaborator> findByIdAndStore_Id(UUID id, UUID storeId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Collaborator c
            join c.roles r
            where c.personId = :personId
              and c.store.id = :storeId
              and c.state.code = 'ACTIVE'
              and r.code = 'OWNER'
            """)
    boolean isOwner(@Param("personId") UUID personId, @Param("storeId") UUID storeId);

    @Query("""
            SELECT DISTINCT permission.code
            FROM Collaborator collaborator
            JOIN collaborator.roles role
            JOIN role.permissions permission
            WHERE collaborator.personId = :personId
              AND collaborator.store.id = :storeId
              AND collaborator.state.code = 'ACTIVE'
            ORDER BY permission.code
            """)
    java.util.List<String> findPermissionCodes(
            @Param("personId") UUID personId,
            @Param("storeId") UUID storeId
    );

    @Query("""
            SELECT CASE WHEN COUNT(permission) > 0 THEN true ELSE false END
            FROM Collaborator collaborator
            JOIN collaborator.roles role
            JOIN role.permissions permission
            WHERE collaborator.personId = :personId
              AND collaborator.store.id = :storeId
              AND collaborator.state.code = 'ACTIVE'
              AND permission.code = :permissionCode
            """)
    boolean hasPermission(
            @Param("personId") UUID personId,
            @Param("storeId") UUID storeId,
            @Param("permissionCode") String permissionCode
    );
}