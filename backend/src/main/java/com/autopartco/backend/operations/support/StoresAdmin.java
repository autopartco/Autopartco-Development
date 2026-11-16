package com.autopartco.backend.operations.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Almacenes de la plataforma para soporte: plan, actividad y equipo (solo lectura). */
@Service
@AllArgsConstructor
public class StoresAdmin {
    private final JdbcClient jdbc;

    public record StoreRow(UUID id, String businessName, String nit, String businessEmail, String state,
            String businessType, OffsetDateTime createdAt, String planName, String applicationStatus,
            OffsetDateTime trialEndsAt, OffsetDateTime paidThrough, long branches, long products, long orders30d,
            BigDecimal revenue30d) {
    }

    public record BranchView(UUID id, String name, String municipality, boolean active) {
    }

    public record MemberView(String name, String email, String roles, String state) {
    }

    public record StoreDetail(StoreRow store, BigDecimal ratingAverage, long ratingCount, long openQuestions,
            long publishedProducts, long stockouts, List<BranchView> branches, List<MemberView> team) {
    }

    private static final String FROM = """
            FROM store s
            LEFT JOIN states st ON st.id = s.state
            LEFT JOIN LATERAL (
                SELECT a.status, a.trial_ends_at, a.paid_through, p.name AS plan_name FROM seller_applications a
                LEFT JOIN seller_plans p ON p.id = COALESCE(a.plan_id, a.selected_plan_id)
                WHERE a.store_id = s.id AND a.deleted_at IS NULL ORDER BY a.created_at DESC LIMIT 1
            ) app ON TRUE
            WHERE s.deleted_at IS NULL
              AND (CAST(:q AS text) IS NULL OR s.business_name ILIKE :q OR s.nit ILIKE :q OR s.business_email ILIKE :q)
              AND (CAST(:state AS text) IS NULL OR st.code = :state)
            """;

    private static final String COLUMNS = """
            SELECT s.id, s.business_name, s.nit, s.business_email, st.code AS state, s.business_type, s.created_at,
                   app.plan_name, app.status AS application_status, app.trial_ends_at, app.paid_through,
                   (SELECT count(*) FROM branches b WHERE b.store_id = s.id AND b.deleted_at IS NULL) AS branches,
                   (SELECT count(*) FROM products p WHERE p.store_id = s.id AND p.deleted_at IS NULL) AS products,
                   (SELECT count(DISTINCT sa.order_id) FROM sales sa JOIN branches b ON b.id = sa.branch_id
                    WHERE b.store_id = s.id AND sa.created_at >= now() - interval '30 days') AS orders_30d,
                   (SELECT COALESCE(sum(sa.total), 0) FROM sales sa JOIN branches b ON b.id = sa.branch_id
                    JOIN states ss ON ss.id = sa.state_id
                    WHERE b.store_id = s.id AND sa.created_at >= now() - interval '30 days'
                      AND ss.code IN ('CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED')) AS revenue_30d
            """;

    @Transactional(readOnly = true)
    public PageResponse<StoreRow> stores(String query, String state, int page, int size) {
        AdminPaging paging = AdminPaging.of(page, size);
        Map<String, Object> params = params(query, state);
        long total = jdbc.sql("SELECT count(*) " + FROM).params(params).query(Long.class).single();
        List<StoreRow> items = jdbc.sql(COLUMNS + FROM + " ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", paging.size()).param("offset", paging.offset())
                .query((rs, n) -> row(rs)).list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    @Transactional(readOnly = true)
    public StoreDetail store(UUID storeId) {
        Map<String, Object> params = params(null, null);
        params.put("id", storeId);
        StoreRow row = jdbc.sql(COLUMNS + FROM + " AND s.id = :id").params(params).query((rs, n) -> row(rs))
                .optional().orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
        record Stats(BigDecimal rating, long ratings, long questions, long published, long stockouts) {
        }
        Stats stats = jdbc.sql("""
                SELECT (SELECT round(avg(r.rating), 2) FROM store_reviews r WHERE r.store_id = :id AND r.deleted_at IS NULL),
                       (SELECT count(*) FROM store_reviews r WHERE r.store_id = :id AND r.deleted_at IS NULL),
                       (SELECT count(*) FROM product_questions q
                        WHERE q.store_id = :id AND q.answer IS NULL AND q.deleted_at IS NULL),
                       (SELECT count(*) FROM search_listings l WHERE l.store_id = :id),
                       (SELECT count(*) FROM inventory i JOIN branches b ON b.id = i.branch_id
                        WHERE b.store_id = :id AND i.deleted_at IS NULL AND i.stock <= 0)
                """).param("id", storeId)
                .query((rs, n) -> new Stats(rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5)))
                .single();
        List<BranchView> branches = jdbc.sql("""
                SELECT b.id, b.name, m.name, (b.deleted_at IS NULL AND COALESCE(bs.code, 'ACTIVE') = 'ACTIVE')
                FROM branches b
                LEFT JOIN states bs ON bs.id = b.state
                LEFT JOIN locations l ON l.id = b.location_id
                LEFT JOIN geo_municipalities m ON m.code = l.municipality_code
                WHERE b.store_id = :id ORDER BY b.name
                """).param("id", storeId)
                .query((rs, n) -> new BranchView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4)))
                .list();
        List<MemberView> team = jdbc.sql("""
                SELECT pe.name || ' ' || pe.surname, acc.email,
                       string_agg(DISTINCT r.name, ', ') AS roles, cs.code
                FROM collaborators c
                JOIN persons pe ON pe.id = c.person_id
                LEFT JOIN states cs ON cs.id = c.state
                LEFT JOIN accounts acc ON acc.id_person = pe.id AND acc.deleted_at IS NULL
                LEFT JOIN roles_collaborators rc ON rc.collaborator_id = c.id AND rc.deleted_at IS NULL
                LEFT JOIN roles r ON r.id = rc.role_id
                WHERE c.collab_store_id = :id AND c.deleted_at IS NULL
                GROUP BY pe.name, pe.surname, acc.email, cs.code
                ORDER BY 1
                """).param("id", storeId)
                .query((rs, n) -> new MemberView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .list();
        return new StoreDetail(row, stats.rating(), stats.ratings(), stats.questions(), stats.published(),
                stats.stockouts(), branches, team);
    }

    private static Map<String, Object> params(String query, String state) {
        Map<String, Object> params = new HashMap<>();
        params.put("q", AdminPaging.like(query));
        params.put("state", state == null || state.isBlank() ? null : state.trim().toUpperCase(java.util.Locale.ROOT));
        return params;
    }

    private static StoreRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoreRow(rs.getObject("id", UUID.class), rs.getString("business_name"), rs.getString("nit"),
                rs.getString("business_email"), rs.getString("state"), rs.getString("business_type"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("plan_name"),
                rs.getString("application_status"), rs.getObject("trial_ends_at", OffsetDateTime.class),
                rs.getObject("paid_through", OffsetDateTime.class), rs.getLong("branches"), rs.getLong("products"),
                rs.getLong("orders_30d"), rs.getBigDecimal("revenue_30d"));
    }
}
