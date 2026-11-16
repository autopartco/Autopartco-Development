package com.autopartco.backend.operations.payments;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Consola de pedidos y pagos de toda la plataforma (solo lectura). */
@Service
@AllArgsConstructor
public class OrdersAdmin {
    private final JdbcClient jdbc;

    public record OrderRow(UUID id, long orderNumber, OffsetDateTime placedAt, String status, String channel,
            BigDecimal total, String customerEmail, String customerName, long stores, String paymentStatus,
            String paymentProvider) {
    }

    public record OrderLine(UUID productId, String name, String sku, int units, BigDecimal unitPrice,
            BigDecimal discount, String storeName, String branchName) {
    }

    public record SaleView(UUID id, String storeName, String branchName, String status, BigDecimal total,
            String fulfillmentType, OffsetDateTime confirmedAt, OffsetDateTime cancelledAt, String cancellationReason) {
    }

    public record PaymentView(UUID id, String status, String provider, BigDecimal amount, BigDecimal refunded,
            int attempt, OffsetDateTime createdAt, OffsetDateTime paidAt, String failureCode, String riskDecision,
            BigDecimal riskScore) {
    }

    public record StatusChange(OffsetDateTime changedAt, String fromStatus, String toStatus, String actorType,
            String reasonCode) {
    }

    public record OrderDetail(OrderRow order, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingTotal,
            OffsetDateTime cancelledAt, String cancellationReason, List<OrderLine> lines, List<SaleView> sales,
            List<PaymentView> payments, List<StatusChange> history) {
    }

    public record PaymentRow(UUID id, OffsetDateTime createdAt, String status, String provider, BigDecimal amount,
            BigDecimal refunded, int attempt, String failureCode, UUID orderId, long orderNumber,
            String customerEmail, String riskDecision) {
    }

    private static final String ORDER_FROM = """
            FROM orders o
            JOIN states st ON st.id = o.state_id
            LEFT JOIN persons pe ON pe.id = o.person_id
            LEFT JOIN accounts acc ON acc.id_person = o.person_id AND acc.deleted_at IS NULL
            LEFT JOIN LATERAL (
                SELECT ps.code, p.provider FROM payments p JOIN payment_statuses ps ON ps.id = p.payment_status_id
                WHERE p.order_id = o.id AND p.deleted_at IS NULL ORDER BY p.created_at DESC LIMIT 1
            ) lp ON TRUE
            WHERE o.deleted_at IS NULL
              AND (CAST(:status AS text) IS NULL OR st.code = :status)
              AND (CAST(:q AS text) IS NULL OR acc.email ILIKE :q OR CAST(o.order_number AS text) ILIKE :q
                   OR (pe.name || ' ' || pe.surname) ILIKE :q)
            """;

    private static final String ORDER_COLUMNS = """
            SELECT o.id, o.order_number, COALESCE(o.placed_at, o.created_at) AS placed_at, st.code AS status, o.channel,
                   o.total, acc.email, pe.name || ' ' || pe.surname AS customer_name,
                   (SELECT count(DISTINCT b.store_id) FROM sales s JOIN branches b ON b.id = s.branch_id
                    WHERE s.order_id = o.id) AS stores,
                   lp.code AS payment_status, lp.provider AS payment_provider
            """;

    @Transactional(readOnly = true)
    public PageResponse<OrderRow> orders(String status, String query, int page, int size) {
        AdminPaging paging = AdminPaging.of(page, size);
        Map<String, Object> params = new HashMap<>();
        params.put("status", upper(status));
        params.put("q", AdminPaging.like(query));
        long total = jdbc.sql("SELECT count(*) " + ORDER_FROM).params(params).query(Long.class).single();
        List<OrderRow> items = jdbc.sql(ORDER_COLUMNS + ORDER_FROM + """
                ORDER BY COALESCE(o.placed_at, o.created_at) DESC LIMIT :limit OFFSET :offset
                """)
                .params(params).param("limit", paging.size()).param("offset", paging.offset())
                .query((rs, n) -> orderRow(rs)).list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    @Transactional(readOnly = true)
    public OrderDetail order(UUID orderId) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", null);
        params.put("q", null);
        params.put("id", orderId);
        OrderRow row = jdbc.sql(ORDER_COLUMNS + ORDER_FROM + " AND o.id = :id").params(params)
                .query((rs, n) -> orderRow(rs)).optional()
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido."));
        record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, OffsetDateTime cancelledAt,
                String reason) {
        }
        Totals totals = jdbc.sql("""
                SELECT subtotal, discount_total, shipping_total, cancelled_at, cancellation_reason_code
                FROM orders WHERE id = :id
                """).param("id", orderId)
                .query((rs, n) -> new Totals(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getString(5))).single();
        List<OrderLine> lines = jdbc.sql("""
                SELECT d.product_id, d.product_name_snapshot, d.sku_snapshot, d.units, d.unit_price,
                       COALESCE(d.discount_amount, 0), st.business_name, b.name
                FROM order_details d
                LEFT JOIN sales s ON s.id = d.sale_id
                LEFT JOIN branches b ON b.id = s.branch_id
                LEFT JOIN store st ON st.id = b.store_id
                WHERE d.order_id = :id AND d.deleted_at IS NULL
                ORDER BY st.business_name, d.product_name_snapshot
                """).param("id", orderId)
                .query((rs, n) -> new OrderLine(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8)))
                .list();
        List<SaleView> sales = jdbc.sql("""
                SELECT s.id, st.business_name, b.name, ss.code, s.total, s.fulfillment_type, s.confirmed_at,
                       s.cancelled_at, s.cancellation_reason_code
                FROM sales s
                JOIN branches b ON b.id = s.branch_id
                JOIN store st ON st.id = b.store_id
                JOIN states ss ON ss.id = s.state_id
                WHERE s.order_id = :id AND s.deleted_at IS NULL
                ORDER BY st.business_name
                """).param("id", orderId)
                .query((rs, n) -> new SaleView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class),
                        rs.getObject(8, OffsetDateTime.class), rs.getString(9)))
                .list();
        List<PaymentView> payments = jdbc.sql("""
                SELECT p.id, ps.code, p.provider, p.amount, COALESCE(p.refunded_amount, 0), COALESCE(p.attempt_number, 1),
                       p.created_at, p.paid_at, p.failure_code, ra.decision, ra.risk_score
                FROM payments p
                JOIN payment_statuses ps ON ps.id = p.payment_status_id
                LEFT JOIN LATERAL (
                    SELECT a.decision, a.risk_score FROM payment_risk_assessments a
                    WHERE a.payment_id = p.id ORDER BY a.assessed_at DESC LIMIT 1
                ) ra ON TRUE
                WHERE p.order_id = :id AND p.deleted_at IS NULL
                ORDER BY p.created_at
                """).param("id", orderId)
                .query((rs, n) -> new PaymentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getInt(6), rs.getObject(7, OffsetDateTime.class),
                        rs.getObject(8, OffsetDateTime.class), rs.getString(9), rs.getString(10), rs.getBigDecimal(11)))
                .list();
        List<StatusChange> history = jdbc.sql("""
                SELECT changed_at, from_status, to_status, actor_type, reason_code
                FROM order_status_history WHERE order_id = :id ORDER BY changed_at
                """).param("id", orderId)
                .query((rs, n) -> new StatusChange(rs.getObject(1, OffsetDateTime.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)))
                .list();
        return new OrderDetail(row, totals.subtotal(), totals.discount(), totals.shipping(), totals.cancelledAt(),
                totals.reason(), lines, sales, payments, history);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentRow> payments(String status, String provider, String query, int page, int size) {
        AdminPaging paging = AdminPaging.of(page, size);
        String from = """
                FROM payments p
                JOIN payment_statuses ps ON ps.id = p.payment_status_id
                JOIN orders o ON o.id = p.order_id
                LEFT JOIN accounts acc ON acc.id_person = o.person_id AND acc.deleted_at IS NULL
                WHERE p.deleted_at IS NULL
                  AND (CAST(:status AS text) IS NULL OR ps.code = :status)
                  AND (CAST(:provider AS text) IS NULL OR p.provider = :provider)
                  AND (CAST(:q AS text) IS NULL OR acc.email ILIKE :q OR CAST(o.order_number AS text) ILIKE :q)
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("status", upper(status));
        params.put("provider", upper(provider));
        params.put("q", AdminPaging.like(query));
        long total = jdbc.sql("SELECT count(*) " + from).params(params).query(Long.class).single();
        List<PaymentRow> items = jdbc.sql("""
                SELECT p.id, p.created_at, ps.code, p.provider, p.amount, COALESCE(p.refunded_amount, 0),
                       COALESCE(p.attempt_number, 1), p.failure_code, o.id AS order_id, o.order_number, acc.email,
                       (SELECT a.decision FROM payment_risk_assessments a WHERE a.payment_id = p.id
                        ORDER BY a.assessed_at DESC LIMIT 1) AS risk_decision
                """ + from + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", paging.size()).param("offset", paging.offset())
                .query((rs, n) -> new PaymentRow(rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class),
                        rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7),
                        rs.getString(8), rs.getObject(9, UUID.class), rs.getLong(10), rs.getString(11),
                        rs.getString(12)))
                .list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    private static OrderRow orderRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderRow(rs.getObject("id", UUID.class), rs.getLong("order_number"),
                rs.getObject("placed_at", OffsetDateTime.class), rs.getString("status"), rs.getString("channel"),
                rs.getBigDecimal("total"), rs.getString("email"), rs.getString("customer_name"), rs.getLong("stores"),
                rs.getString("payment_status"), rs.getString("payment_provider"));
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
