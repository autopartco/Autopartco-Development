package com.autopartco.backend.core.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

/**
 * Permisos de plataforma ({@code PLATFORM_*}) de un miembro del equipo ({@code platform_staff}) activo y con un rol
 * vigente. Sin membresía activa la lista está vacía: el token solo lleva {@code ROLE_STAFF} y no abre ninguna sección.
 */
@Component
@AllArgsConstructor
public class StaffAuthorities {
    static final String SQL = """
            SELECT rp.permission_code
            FROM platform_staff s
            JOIN platform_roles r ON r.id = s.role_id AND r.deleted_at IS NULL
            JOIN platform_role_permissions rp ON rp.role_id = r.id
            WHERE s.account_id = :accountId AND s.active AND s.deleted_at IS NULL
            ORDER BY rp.permission_code
            """;

    private final JdbcClient jdbc;

    public List<String> permissionsOf(UUID accountId) {
        return jdbc.sql(SQL).param("accountId", accountId).query(String.class).list();
    }
}
