package com.autopartco.backend.operations.support;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountAdministration;
import com.autopartco.backend.operations.AdminActor;
import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Cuentas de usuarios para soporte: búsqueda, detalle (sesiones, ingresos, almacenes, pedidos) y acciones de bloqueo,
 * activación y cierre de sesiones. Nadie puede actuar sobre una cuenta {@code ROOT} ni sobre la suya; las cuentas del
 * equipo ({@code STAFF}) solo las gestiona {@code ROOT}.
 */
@Service
@AllArgsConstructor
public class AccountsAdmin {
    private final JdbcClient jdbc;
    private final AccountAdministration administration;

    public record AccountRow(UUID id, String email, String name, String type, String state, OffsetDateTime createdAt,
            OffsetDateTime lastLoginAt, long activeSessions, long orders) {
    }

    public record SessionView(UUID id, String ipAddress, String userAgent, OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
    }

    public record LoginView(OffsetDateTime attemptedAt, boolean successful, String ipAddress) {
    }

    public record StoreMembership(UUID storeId, String storeName, String roles) {
    }

    public record AccountDetail(AccountRow account, String phone, boolean twoFactor, List<SessionView> sessions,
            List<LoginView> logins, List<StoreMembership> stores, long failedLogins7d) {
    }

    private static final String FROM = """
            FROM accounts a
            JOIN types_accounts t ON t.id = a.type_id
            JOIN states st ON st.id = a.state_id
            LEFT JOIN persons pe ON pe.id = a.id_person
            WHERE a.deleted_at IS NULL
              AND (CAST(:q AS text) IS NULL OR a.email ILIKE :q OR (pe.name || ' ' || pe.surname) ILIKE :q
                   OR pe.identification ILIKE :q)
              AND (CAST(:type AS text) IS NULL OR t.code = :type)
              AND (CAST(:state AS text) IS NULL OR st.code = :state)
            """;

    private static final String COLUMNS = """
            SELECT a.id, a.email, pe.name || ' ' || pe.surname AS name, t.code AS type, st.code AS state, a.created_at,
                   (SELECT max(l.attempted_at) FROM login_attempts l WHERE l.account_id = a.id AND l.successful)
                       AS last_login,
                   (SELECT count(*) FROM account_sessions s WHERE s.account_id = a.id AND s.revoked_at IS NULL
                    AND s.deleted_at IS NULL AND s.expired_at > now()) AS sessions,
                   (SELECT count(*) FROM orders o WHERE o.person_id = a.id_person AND o.deleted_at IS NULL) AS orders
            """;

    @Transactional(readOnly = true)
    public PageResponse<AccountRow> accounts(String query, String type, String state, int page, int size) {
        AdminPaging paging = AdminPaging.of(page, size);
        Map<String, Object> params = new HashMap<>();
        params.put("q", AdminPaging.like(query));
        params.put("type", upper(type));
        params.put("state", upper(state));
        long total = jdbc.sql("SELECT count(*) " + FROM).params(params).query(Long.class).single();
        List<AccountRow> items = jdbc.sql(COLUMNS + FROM + " ORDER BY a.created_at DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", paging.size()).param("offset", paging.offset())
                .query((rs, n) -> row(rs)).list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    @Transactional(readOnly = true)
    public AccountDetail account(UUID accountId) {
        AccountRow row = require(accountId);
        record Extra(String phone, boolean twoFactor) {
        }
        Extra extra = jdbc.sql("""
                SELECT pe.number_phone,
                       COALESCE(CAST(a.configuration -> 'security' -> 'twoFactor' ->> 'enabled' AS boolean), false)
                FROM accounts a LEFT JOIN persons pe ON pe.id = a.id_person WHERE a.id = :id
                """).param("id", accountId)
                .query((rs, n) -> new Extra(rs.getString(1), rs.getBoolean(2))).single();
        List<SessionView> sessions = jdbc.sql("""
                SELECT id, host(ip_address), user_agent, created_at, expired_at FROM account_sessions
                WHERE account_id = :id AND revoked_at IS NULL AND deleted_at IS NULL AND expired_at > now()
                ORDER BY created_at DESC LIMIT 20
                """).param("id", accountId)
                .query((rs, n) -> new SessionView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class)))
                .list();
        List<LoginView> logins = jdbc.sql("""
                SELECT attempted_at, successful, host(ip_address) FROM login_attempts
                WHERE account_id = :id ORDER BY attempted_at DESC LIMIT 20
                """).param("id", accountId)
                .query((rs, n) -> new LoginView(rs.getObject(1, OffsetDateTime.class), rs.getBoolean(2),
                        rs.getString(3)))
                .list();
        List<StoreMembership> stores = jdbc.sql("""
                SELECT s.id, s.business_name, string_agg(DISTINCT r.name, ', ')
                FROM accounts a
                JOIN collaborators c ON c.person_id = a.id_person AND c.deleted_at IS NULL
                JOIN store s ON s.id = c.collab_store_id
                LEFT JOIN roles_collaborators rc ON rc.collaborator_id = c.id AND rc.deleted_at IS NULL
                LEFT JOIN roles r ON r.id = rc.role_id
                WHERE a.id = :id
                GROUP BY s.id, s.business_name ORDER BY s.business_name
                """).param("id", accountId)
                .query((rs, n) -> new StoreMembership(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .list();
        long failed = jdbc.sql("""
                SELECT count(*) FROM login_attempts
                WHERE account_id = :id AND NOT successful AND attempted_at >= now() - interval '7 days'
                """).param("id", accountId).query(Long.class).single();
        return new AccountDetail(row, extra.phone(), extra.twoFactor(), sessions, logins, stores, failed);
    }

    @Transactional
    public void setActive(UUID accountId, boolean active, AdminActor.Actor actor) {
        guard(accountId, actor);
        administration.setActive(accountId, active, actor.accountId());
    }

    @Transactional
    public void revokeSessions(UUID accountId, AdminActor.Actor actor) {
        guard(accountId, actor);
        administration.revokeSessions(accountId, actor.accountId());
    }

    private void guard(UUID accountId, AdminActor.Actor actor) {
        requireActionAllowed(require(accountId), actor);
    }

    /** Regla pura del guardián: ni la propia cuenta ni {@code ROOT}; {@code STAFF} solo si actúa {@code ROOT}. */
    public static void requireActionAllowed(AccountRow target, AdminActor.Actor actor) {
        if (target.id().equals(actor.accountId())) {
            throw new ForbiddenException("ADMIN_SELF_ACTION", "No puedes hacer esta acción sobre tu propia cuenta.");
        }
        if ("ROOT".equals(target.type())) {
            throw new ForbiddenException("ADMIN_ROOT_PROTECTED", "La cuenta root no se puede modificar desde la consola.");
        }
        if ("STAFF".equals(target.type()) && !actor.root()) {
            throw new ForbiddenException("ADMIN_STAFF_PROTECTED", "Solo root puede gestionar cuentas del equipo.");
        }
    }

    private AccountRow require(UUID accountId) {
        Map<String, Object> params = new HashMap<>();
        params.put("q", null);
        params.put("type", null);
        params.put("state", null);
        params.put("id", accountId);
        return jdbc.sql(COLUMNS + FROM + " AND a.id = :id").params(params).query((rs, n) -> row(rs)).optional()
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
    }

    private static AccountRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AccountRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("name"),
                rs.getString("type"), rs.getString("state"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("last_login", OffsetDateTime.class), rs.getLong("sessions"), rs.getLong("orders"));
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
