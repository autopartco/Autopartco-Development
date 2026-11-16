package com.autopartco.backend.operations.anomalies;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Evaluaciones de riesgo de pagos ({@code payment_risk_assessments}): las del modelo {@code payment_anomaly}
 * (Isolation Forest) y cualquier otra etapa. La revisión manual guarda el resultado con quién lo marcó.
 */
@Service
@AllArgsConstructor
public class PaymentRiskAdmin {
    static final Set<String> OUTCOMES = Set.of("LEGIT", "FRAUD_CONFIRMED", "UNKNOWN");
    static final int SUMMARY_DAYS = 30;

    private final JdbcClient jdbc;
    private final AuditService auditService;

    public record Assessment(long id, OffsetDateTime assessedAt, String stage, String decision, BigDecimal riskScore,
            BigDecimal threshold, String model, String modelVersion, String outcome, OffsetDateTime outcomeAt,
            String reviewedBy, UUID paymentId, BigDecimal amount, String provider, String paymentStatus,
            OffsetDateTime paymentCreatedAt, Integer attemptNumber, UUID orderId, Long orderNumber,
            String customerEmail) {
    }

    public record Summary(int days, long total, long review, long pendingReview, long legit, long fraudConfirmed,
            BigDecimal averageScore, BigDecimal reviewAmount) {
    }

    /**
     * @param decision {@code ALLOW}, {@code REVIEW}, {@code DENY} o nulo
     * @param status {@code PENDING} (REVIEW sin resultado), {@code REVIEWED} o nulo
     */
    @Transactional(readOnly = true)
    public PageResponse<Assessment> list(String decision, String status, String query, int page, int size) {
        AdminPaging paging = AdminPaging.of(page, size);
        String where = """
                WHERE (CAST(:decision AS text) IS NULL OR a.decision = :decision)
                  AND (CAST(:status AS text) IS NULL
                       OR (:status = 'PENDING' AND a.decision = 'REVIEW' AND a.outcome IS NULL)
                       OR (:status = 'REVIEWED' AND a.outcome IS NOT NULL))
                  AND (CAST(:q AS text) IS NULL OR acc.email ILIKE :q OR CAST(o.order_number AS text) ILIKE :q)
                """;
        String from = """
                FROM payment_risk_assessments a
                JOIN payments p ON p.id = a.payment_id
                JOIN orders o ON o.id = a.order_id
                LEFT JOIN payment_statuses ps ON ps.id = p.payment_status_id
                LEFT JOIN accounts acc ON acc.id_person = o.person_id AND acc.deleted_at IS NULL
                LEFT JOIN accounts rev ON rev.id = a.reviewed_by_account_id
                """;
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("decision", blankToNull(decision));
        params.put("status", blankToNull(status));
        params.put("q", AdminPaging.like(query));
        long total = jdbc.sql("SELECT count(*) " + from + where).params(params).query(Long.class).single();
        List<Assessment> items = jdbc.sql("""
                SELECT a.id, a.assessed_at, a.stage, a.decision, a.risk_score,
                       CAST(a.signals ->> 'threshold' AS numeric) AS threshold, a.signals ->> 'model' AS model,
                       a.signals ->> 'version' AS model_version, a.outcome, a.outcome_at, rev.email AS reviewed_by,
                       p.id AS payment_id, p.amount, p.provider, ps.code AS payment_status, p.created_at,
                       p.attempt_number, o.id AS order_id, o.order_number, acc.email AS customer_email
                """ + from + where + """
                ORDER BY (a.decision = 'REVIEW' AND a.outcome IS NULL) DESC, a.risk_score DESC NULLS LAST,
                         a.assessed_at DESC
                LIMIT :limit OFFSET :offset
                """)
                .params(params)
                .param("limit", paging.size())
                .param("offset", paging.offset())
                .query((rs, n) -> new Assessment(rs.getLong("id"), rs.getObject("assessed_at", OffsetDateTime.class),
                        rs.getString("stage"), rs.getString("decision"), rs.getBigDecimal("risk_score"),
                        rs.getBigDecimal("threshold"), rs.getString("model"), rs.getString("model_version"),
                        rs.getString("outcome"), rs.getObject("outcome_at", OffsetDateTime.class),
                        rs.getString("reviewed_by"), rs.getObject("payment_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getString("provider"), rs.getString("payment_status"),
                        rs.getObject("created_at", OffsetDateTime.class), (Integer) rs.getObject("attempt_number"),
                        rs.getObject("order_id", UUID.class), (Long) rs.getObject("order_number"),
                        rs.getString("customer_email")))
                .list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return jdbc.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE a.decision = 'REVIEW') AS review,
                       count(*) FILTER (WHERE a.decision = 'REVIEW' AND a.outcome IS NULL) AS pending,
                       count(*) FILTER (WHERE a.outcome = 'LEGIT') AS legit,
                       count(*) FILTER (WHERE a.outcome IN ('FRAUD_CONFIRMED', 'CHARGEBACK', 'FRIENDLY_FRAUD')) AS fraud,
                       round(avg(a.risk_score), 4) AS avg_score,
                       COALESCE(sum(p.amount) FILTER (WHERE a.decision = 'REVIEW'), 0) AS review_amount
                FROM payment_risk_assessments a
                JOIN payments p ON p.id = a.payment_id
                WHERE a.assessed_at >= now() - make_interval(days => :days)
                """)
                .param("days", SUMMARY_DAYS)
                .query((rs, n) -> new Summary(SUMMARY_DAYS, rs.getLong("total"), rs.getLong("review"),
                        rs.getLong("pending"), rs.getLong("legit"), rs.getLong("fraud"), rs.getBigDecimal("avg_score"),
                        rs.getBigDecimal("review_amount")))
                .single();
    }

    /** Marca el resultado de la revisión manual ({@code LEGIT}, {@code FRAUD_CONFIRMED} o {@code UNKNOWN}). */
    @Transactional
    public void markOutcome(long assessmentId, String outcome, UUID reviewerAccountId) {
        String code = outcome == null ? "" : outcome.trim().toUpperCase(java.util.Locale.ROOT);
        if (!OUTCOMES.contains(code)) {
            throw new BadRequestException("INVALID_OUTCOME", "El resultado debe ser LEGIT, FRAUD_CONFIRMED o UNKNOWN.");
        }
        int updated = jdbc.sql("""
                UPDATE payment_risk_assessments
                SET outcome = :outcome, outcome_at = now(), outcome_source = 'MANUAL_REVIEW',
                    reviewed_by_account_id = :reviewer
                WHERE id = :id
                """)
                .param("outcome", code)
                .param("reviewer", reviewerAccountId)
                .param("id", assessmentId)
                .update();
        if (updated == 0) {
            throw new NotFoundException("ASSESSMENT_NOT_FOUND", "No se encontró la evaluación.");
        }
        auditService.record(reviewerAccountId, "PAYMENT_RISK_REVIEWED", "PAYMENT_RISK_ASSESSMENT", null,
                Map.of("assessmentId", assessmentId, "outcome", code));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
