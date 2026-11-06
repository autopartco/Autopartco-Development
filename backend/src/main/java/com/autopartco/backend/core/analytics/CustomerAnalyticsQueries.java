package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Actividad de compra por persona (read model sobre {@code sales}, {@code orders}, {@code states},
 * {@code branches} y {@code persons}) y datos de contacto con consentimiento de marketing ({@code accounts},
 * {@code person_consents_current}). Almacén opcional: sin él, toda la plataforma.
 */
@Repository
@AllArgsConstructor
class CustomerAnalyticsQueries {
    static final String ACTIVITY_SQL = """
            SELECT o.person_id,
                   trim(concat_ws(' ', pe.name, pe.surname)) AS name,
                   count(*) FILTER (WHERE s.created_at >= :windowStart) AS orders,
                   coalesce(sum(s.total) FILTER (WHERE s.created_at >= :windowStart), 0) AS revenue,
                   count(DISTINCT b.store_id) FILTER (WHERE s.created_at >= :windowStart) AS stores,
                   min(s.created_at) AS first_order_at,
                   max(s.created_at) AS last_order_at
            FROM sales s
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            JOIN orders o ON o.id = s.order_id
            JOIN persons pe ON pe.id = o.person_id
            WHERE s.deleted_at IS NULL
              AND st.code IN %s
              AND (CAST(:storeId AS uuid) IS NULL OR b.store_id = CAST(:storeId AS uuid))
            GROUP BY o.person_id, pe.name, pe.surname
            """.formatted(Analytics.VALID_SALE_STATES);

    /** Igual que {@link #ACTIVITY_SQL} pero solo con ventas anteriores a {@code :until} (foto a una fecha pasada). */
    static final String ACTIVITY_UNTIL_SQL = ACTIVITY_SQL.replace("WHERE s.deleted_at IS NULL",
            "WHERE s.deleted_at IS NULL\n              AND s.created_at < :until");

    static final String CONTACTS_SQL = """
            SELECT pe.id AS person_id,
                   (SELECT a.email FROM accounts a
                    WHERE a.id_person = pe.id AND a.deleted_at IS NULL
                    ORDER BY a.email LIMIT 1) AS email,
                   coalesce((SELECT c.granted FROM person_consents_current c
                             WHERE c.person_id = pe.id AND c.purpose_code = 'MARKETING_EMAIL'), false)
                       AS marketing_email,
                   coalesce((SELECT c.granted FROM person_consents_current c
                             WHERE c.person_id = pe.id AND c.purpose_code = 'MARKETING_WHATSAPP'), false)
                       AS marketing_whatsapp
            FROM persons pe
            WHERE pe.id IN (:personIds)
            """;

    private final JdbcClient jdbcClient;

    record Activity(UUID personId, String name, long orders, BigDecimal revenue, long stores,
                    OffsetDateTime firstOrderAt, OffsetDateTime lastOrderAt) {
    }

    record Contact(UUID personId, String email, boolean marketingEmail, boolean marketingWhatsapp) {
    }

    List<Activity> activity(UUID storeId, OffsetDateTime windowStart) {
        return jdbcClient.sql(ACTIVITY_SQL)
                .param("storeId", storeId == null ? null : storeId.toString())
                .param("windowStart", windowStart)
                .query((result, row) -> new Activity(
                        result.getObject("person_id", UUID.class),
                        result.getString("name"),
                        result.getLong("orders"),
                        Analytics.nonNull(result.getBigDecimal("revenue")),
                        result.getLong("stores"),
                        result.getObject("first_order_at", OffsetDateTime.class),
                        result.getObject("last_order_at", OffsetDateTime.class)))
                .list();
    }

    /** Actividad como estaba antes de {@code until}: ventana desde {@code windowStart} y última compra anterior. */
    List<Activity> activityUntil(UUID storeId, OffsetDateTime windowStart, OffsetDateTime until) {
        return jdbcClient.sql(ACTIVITY_UNTIL_SQL)
                .param("storeId", storeId == null ? null : storeId.toString())
                .param("windowStart", windowStart)
                .param("until", until)
                .query((result, row) -> new Activity(
                        result.getObject("person_id", UUID.class),
                        result.getString("name"),
                        result.getLong("orders"),
                        Analytics.nonNull(result.getBigDecimal("revenue")),
                        result.getLong("stores"),
                        result.getObject("first_order_at", OffsetDateTime.class),
                        result.getObject("last_order_at", OffsetDateTime.class)))
                .list();
    }

    List<Contact> contacts(Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(CONTACTS_SQL)
                .param("personIds", personIds)
                .query((result, row) -> new Contact(
                        result.getObject("person_id", UUID.class),
                        result.getString("email"),
                        result.getBoolean("marketing_email"),
                        result.getBoolean("marketing_whatsapp")))
                .list();
    }
}
