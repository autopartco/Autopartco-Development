package com.autopartco.backend.operations.staff;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountAdministration;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.identity.StaffAccountDetails;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Equipo de la plataforma: miembros ({@code platform_staff}), roles ({@code platform_roles}) y su catálogo de
 * permisos. Solo lo usa {@code ROOT} (lo exige el controlador). Al cambiar el rol, desactivar o quitar a un miembro se
 * cierran sus sesiones para que los permisos nuevos apliquen al volver a ingresar.
 */
@Service
@AllArgsConstructor
public class PlatformStaff {
    private final JdbcClient jdbc;
    private final AccountAdministration administration;
    private final AuditService auditService;

    public record Permission(String code, String description) {
    }

    public record Role(UUID id, String code, String name, String description, boolean system, List<String> permissions,
            long members) {
    }

    public record Member(UUID accountId, String email, String name, UUID roleId, String roleName, boolean active,
            String accountState, OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
    }

    public record NewMember(String email, String name, String surname, String identification, LocalDate dateBirth,
            String numberPhone, UUID roleId) {
    }

    public record RoleInput(String name, String description, List<String> permissions) {
    }

    @Transactional(readOnly = true)
    public List<Permission> permissions() {
        return jdbc.sql("SELECT code, description FROM platform_permissions ORDER BY sort_order, code")
                .query((rs, n) -> new Permission(rs.getString(1), rs.getString(2))).list();
    }

    @Transactional(readOnly = true)
    public List<Role> roles() {
        return jdbc.sql("""
                SELECT r.id, r.code, r.name, r.description, r.is_system,
                       COALESCE(array_to_string(array_agg(rp.permission_code ORDER BY rp.permission_code)
                                FILTER (WHERE rp.permission_code IS NOT NULL), ','), '') AS permissions,
                       (SELECT count(*) FROM platform_staff s WHERE s.role_id = r.id AND s.deleted_at IS NULL) AS members
                FROM platform_roles r
                LEFT JOIN platform_role_permissions rp ON rp.role_id = r.id
                WHERE r.deleted_at IS NULL
                GROUP BY r.id
                ORDER BY r.is_system DESC, r.name
                """)
                .query((rs, n) -> new Role(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5), split(rs.getString(6)), rs.getLong(7)))
                .list();
    }

    @Transactional(readOnly = true)
    public List<Member> members() {
        return jdbc.sql("""
                SELECT a.id, a.email, pe.name || ' ' || pe.surname, r.id, r.name, s.active, st.code, s.created_at,
                       (SELECT max(l.attempted_at) FROM login_attempts l WHERE l.account_id = a.id AND l.successful)
                FROM platform_staff s
                JOIN accounts a ON a.id = s.account_id
                JOIN states st ON st.id = a.state_id
                LEFT JOIN persons pe ON pe.id = a.id_person
                JOIN platform_roles r ON r.id = s.role_id
                WHERE s.deleted_at IS NULL
                ORDER BY s.active DESC, pe.name
                """)
                .query((rs, n) -> new Member(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getBoolean(6), rs.getString(7),
                        rs.getObject(8, OffsetDateTime.class), rs.getObject(9, OffsetDateTime.class)))
                .list();
    }

    /** Crea la cuenta {@code STAFF}, la asigna al rol y envía el correo para definir la contraseña. */
    @Transactional
    public Member addMember(NewMember input, UUID actorAccountId) {
        requireRole(input.roleId());
        AccountRef account = administration.createStaffAccount(input.email(), new StaffAccountDetails(input.name(),
                input.surname(), input.identification(), input.dateBirth(), input.numberPhone()), actorAccountId);
        jdbc.sql("""
                INSERT INTO platform_staff (account_id, role_id, created_by_account_id) VALUES (:account, :role, :actor)
                """).param("account", account.accountId()).param("role", input.roleId()).param("actor", actorAccountId)
                .update();
        auditService.record(actorAccountId, "STAFF_MEMBER_ADDED", "ACCOUNT", account.accountId(),
                Map.of("roleId", input.roleId().toString()));
        return member(account.accountId());
    }

    /** Cambia el rol o el estado. Desactivar inactiva la cuenta y cierra sesiones; activar la reactiva. */
    @Transactional
    public Member updateMember(UUID accountId, UUID roleId, Boolean active, UUID actorAccountId) {
        Member current = member(accountId);
        if (accountId.equals(actorAccountId)) {
            throw new ForbiddenException("ADMIN_SELF_ACTION", "No puedes cambiar tu propio acceso.");
        }
        if (roleId != null && !roleId.equals(current.roleId())) {
            requireRole(roleId);
            jdbc.sql("UPDATE platform_staff SET role_id = :role, updated_at = now() WHERE account_id = :account")
                    .param("role", roleId).param("account", accountId).update();
            administration.revokeSessions(accountId, actorAccountId);
            auditService.record(actorAccountId, "STAFF_ROLE_CHANGED", "ACCOUNT", accountId,
                    Map.of("from", current.roleId().toString(), "to", roleId.toString()));
        }
        if (active != null && active != current.active()) {
            jdbc.sql("UPDATE platform_staff SET active = :active, updated_at = now() WHERE account_id = :account")
                    .param("active", active).param("account", accountId).update();
            administration.setActive(accountId, active, actorAccountId);
        }
        return member(accountId);
    }

    /** Quita al miembro del equipo y desactiva su cuenta (queda en la auditoría). */
    @Transactional
    public void removeMember(UUID accountId, UUID actorAccountId) {
        member(accountId);
        if (accountId.equals(actorAccountId)) {
            throw new ForbiddenException("ADMIN_SELF_ACTION", "No puedes quitarte del equipo.");
        }
        jdbc.sql("UPDATE platform_staff SET deleted_at = now(), active = false, updated_at = now() WHERE account_id = :a")
                .param("a", accountId).update();
        administration.setActive(accountId, false, actorAccountId);
        auditService.record(actorAccountId, "STAFF_MEMBER_REMOVED", "ACCOUNT", accountId, Map.of());
    }

    @Transactional
    public Role createRole(RoleInput input, UUID actorAccountId) {
        String name = requireName(input.name());
        String code = codeOf(name);
        boolean exists = jdbc.sql("SELECT count(*) > 0 FROM platform_roles WHERE code = :code AND deleted_at IS NULL")
                .param("code", code).query(Boolean.class).single();
        if (exists) {
            throw new ConflictException("PLATFORM_ROLE_EXISTS", "Ya existe un rol con ese nombre.");
        }
        UUID id = jdbc.sql("""
                INSERT INTO platform_roles (code, name, description) VALUES (:code, :name, :description) RETURNING id
                """).param("code", code).param("name", name).param("description", blank(input.description()))
                .query(UUID.class).single();
        setPermissions(id, input.permissions());
        auditService.record(actorAccountId, "PLATFORM_ROLE_CREATED", "PLATFORM_ROLE", id, Map.of("code", code));
        return role(id);
    }

    /** Los roles del sistema conservan su código; se pueden renombrar y cambiar sus permisos. */
    @Transactional
    public Role updateRole(UUID roleId, RoleInput input, UUID actorAccountId) {
        requireRole(roleId);
        jdbc.sql("""
                UPDATE platform_roles SET name = :name, description = :description, updated_at = now() WHERE id = :id
                """).param("name", requireName(input.name())).param("description", blank(input.description()))
                .param("id", roleId).update();
        setPermissions(roleId, input.permissions());
        // Los miembros con este rol vuelven a ingresar para recibir los permisos nuevos.
        jdbc.sql("SELECT account_id FROM platform_staff WHERE role_id = :id AND deleted_at IS NULL AND active")
                .param("id", roleId).query(UUID.class).list()
                .forEach(accountId -> administration.revokeSessions(accountId, actorAccountId));
        auditService.record(actorAccountId, "PLATFORM_ROLE_UPDATED", "PLATFORM_ROLE", roleId,
                Map.of("permissions", String.join(",", input.permissions() == null ? List.of() : input.permissions())));
        return role(roleId);
    }

    @Transactional
    public void deleteRole(UUID roleId, UUID actorAccountId) {
        Role role = role(roleId);
        if (role.system()) {
            throw new ConflictException("PLATFORM_ROLE_SYSTEM", "Los roles predefinidos no se pueden eliminar.");
        }
        if (role.members() > 0) {
            throw new ConflictException("PLATFORM_ROLE_IN_USE", "Reasigna a los miembros antes de eliminar el rol.");
        }
        jdbc.sql("UPDATE platform_roles SET deleted_at = now() WHERE id = :id").param("id", roleId).update();
        auditService.record(actorAccountId, "PLATFORM_ROLE_DELETED", "PLATFORM_ROLE", roleId, Map.of());
    }

    // ---- Apoyo ---------------------------------------------------------------------------------------------------

    private void setPermissions(UUID roleId, List<String> requested) {
        Set<String> codes = new LinkedHashSet<>(requested == null ? List.of() : requested);
        Set<String> valid = new java.util.HashSet<>(permissions().stream().map(Permission::code).toList());
        for (String code : codes) {
            if (!valid.contains(code)) {
                throw new BadRequestException("INVALID_PERMISSION", "Permiso desconocido: " + code);
            }
        }
        jdbc.sql("DELETE FROM platform_role_permissions WHERE role_id = :id").param("id", roleId).update();
        for (String code : codes) {
            jdbc.sql("INSERT INTO platform_role_permissions (role_id, permission_code) VALUES (:id, :code)")
                    .param("id", roleId).param("code", code).update();
        }
    }

    private Role role(UUID roleId) {
        return roles().stream().filter(r -> r.id().equals(roleId)).findFirst()
                .orElseThrow(() -> new NotFoundException("PLATFORM_ROLE_NOT_FOUND", "No se encontró el rol."));
    }

    private void requireRole(UUID roleId) {
        if (roleId == null) {
            throw new BadRequestException("ROLE_REQUIRED", "Elige un rol.");
        }
        role(roleId);
    }

    private Member member(UUID accountId) {
        return members().stream().filter(m -> m.accountId().equals(accountId)).findFirst()
                .orElseThrow(() -> new NotFoundException("STAFF_MEMBER_NOT_FOUND", "No se encontró el miembro del equipo."));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new BadRequestException("INVALID_ROLE_NAME", "El nombre del rol es obligatorio (máx. 100 caracteres).");
        }
        return name.trim();
    }

    /** "Soporte nivel 2" → {@code SOPORTE_NIVEL_2}. */
    static String codeOf(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty() || !Character.isLetter(code.charAt(0))) {
            code = "ROL_" + code;
        }
        return code.length() > 60 ? code.substring(0, 60) : code;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
