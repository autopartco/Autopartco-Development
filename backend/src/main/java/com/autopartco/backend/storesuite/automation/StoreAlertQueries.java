package com.autopartco.backend.storesuite.automation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.autopartco.backend.core.analytics.AlertRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/** Lecturas y escrituras JDBC de {@code store_alerts} y {@code alert_preferences}. No comprueba permisos. */
@Repository
@AllArgsConstructor
class StoreAlertQueries {
    static final int LIST_LIMIT = 100;

    private static final String COLUMNS = """
            id, type, severity, title, message, payload::text AS payload, created_at, read_at, resolved_at
            """;

    static final String INSERT_SQL = """
            INSERT INTO store_alerts (store_id, type, severity, title, message, payload, dedupe_key)
            VALUES (:storeId, :type, :severity, :title, :message, CAST(:payload AS jsonb), :dedupeKey)
            ON CONFLICT (store_id, dedupe_key) DO NOTHING
            """;

    static final String LIST_SQL = "SELECT " + COLUMNS + """
            FROM store_alerts
            WHERE store_id = :storeId
              AND (:onlyOpen = false OR resolved_at IS NULL)
              AND (:onlyUnread = false OR read_at IS NULL)
            ORDER BY created_at DESC, id
            LIMIT\s""" + LIST_LIMIT;

    static final String SUMMARY_SQL = """
            SELECT count(*) FILTER (WHERE read_at IS NULL) AS unread,
                   count(*) FILTER (WHERE severity = 'CRITICAL') AS critical
            FROM store_alerts
            WHERE store_id = :storeId AND resolved_at IS NULL
            """;

    static final String UPDATE_SQL = """
            UPDATE store_alerts
            SET read_at = CASE WHEN :read THEN coalesce(read_at, now()) ELSE read_at END,
                resolved_at = CASE WHEN :resolved THEN coalesce(resolved_at, now()) ELSE resolved_at END
            WHERE id = :alertId AND store_id = :storeId
            RETURNING\s""" + COLUMNS;

    static final String RESOLVE_ALL_OF_TYPE_SQL = """
            UPDATE store_alerts SET resolved_at = now()
            WHERE store_id = :storeId AND type = :type AND resolved_at IS NULL
            """;

    static final String RESOLVE_OTHERS_SQL = RESOLVE_ALL_OF_TYPE_SQL + " AND dedupe_key NOT IN (:keepKeys)";

    static final String PREFERENCES_SQL = """
            SELECT type, in_app, email, threshold::text AS threshold
            FROM alert_preferences
            WHERE store_id = :storeId
            """;

    static final String UPSERT_PREFERENCE_SQL = """
            INSERT INTO alert_preferences (store_id, type, in_app, email, threshold, updated_at)
            VALUES (:storeId, :type, :inApp, :email, CAST(:threshold AS jsonb), now())
            ON CONFLICT (store_id, type) DO UPDATE
            SET in_app = EXCLUDED.in_app, email = EXCLUDED.email, threshold = EXCLUDED.threshold,
                updated_at = now()
            """;

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /** {@code true} si la fila se insertó; {@code false} si ya existía una con el mismo {@code dedupe_key}. */
    boolean insertIfAbsent(UUID storeId, AlertRules.Candidate candidate) {
        return jdbcClient.sql(INSERT_SQL)
                .param("storeId", storeId)
                .param("type", candidate.type().name())
                .param("severity", candidate.severity().name())
                .param("title", candidate.title())
                .param("message", candidate.message())
                .param("payload", json(candidate.payload()))
                .param("dedupeKey", candidate.dedupeKey())
                .update() > 0;
    }

    List<AlertResponse> list(UUID storeId, boolean onlyOpen, boolean onlyUnread) {
        return jdbcClient.sql(LIST_SQL)
                .param("storeId", storeId)
                .param("onlyOpen", onlyOpen)
                .param("onlyUnread", onlyUnread)
                .query(this::mapAlert)
                .list();
    }

    AlertSummaryResponse summary(UUID storeId) {
        return jdbcClient.sql(SUMMARY_SQL)
                .param("storeId", storeId)
                .query((result, row) -> new AlertSummaryResponse(result.getLong("unread"), result.getLong("critical")))
                .single();
    }

    /** Vacío si la alerta no existe o es de otro almacén. */
    Optional<AlertResponse> update(UUID storeId, UUID alertId, boolean read, boolean resolved) {
        return jdbcClient.sql(UPDATE_SQL)
                .param("storeId", storeId)
                .param("alertId", alertId)
                .param("read", read)
                .param("resolved", resolved)
                .query(this::mapAlert)
                .optional();
    }

    /** Resuelve las alertas abiertas del tipo cuyo {@code dedupe_key} no está en {@code keepKeys}. */
    int resolveOthers(UUID storeId, AlertRules.Type type, Collection<String> keepKeys) {
        if (keepKeys.isEmpty()) {
            return jdbcClient.sql(RESOLVE_ALL_OF_TYPE_SQL)
                    .param("storeId", storeId)
                    .param("type", type.name())
                    .update();
        }
        return jdbcClient.sql(RESOLVE_OTHERS_SQL)
                .param("storeId", storeId)
                .param("type", type.name())
                .param("keepKeys", keepKeys)
                .update();
    }

    /** Preferencias guardadas (solo los tipos con fila). */
    Map<AlertRules.Type, AlertPreferenceResponse> preferences(UUID storeId) {
        Map<AlertRules.Type, AlertPreferenceResponse> byType = new LinkedHashMap<>();
        jdbcClient.sql(PREFERENCES_SQL)
                .param("storeId", storeId)
                .query((result, row) -> {
                    AlertRules.Type type = AlertRules.Type.valueOf(result.getString("type"));
                    return Map.entry(type, new AlertPreferenceResponse(type.name(), result.getBoolean("in_app"),
                            result.getBoolean("email"), parse(result.getString("threshold"))));
                })
                .list()
                .forEach(entry -> byType.put(entry.getKey(), entry.getValue()));
        return byType;
    }

    void savePreference(UUID storeId, AlertPreferenceResponse preference) {
        jdbcClient.sql(UPSERT_PREFERENCE_SQL)
                .param("storeId", storeId)
                .param("type", preference.type())
                .param("inApp", preference.inApp())
                .param("email", preference.email())
                .param("threshold", preference.threshold() == null ? null : json(preference.threshold()))
                .update();
    }

    private AlertResponse mapAlert(ResultSet result, int row) throws SQLException {
        return new AlertResponse(
                result.getObject("id", UUID.class),
                result.getString("type"),
                result.getString("severity"),
                result.getString("title"),
                result.getString("message"),
                parse(result.getString("payload")),
                result.getObject("created_at", OffsetDateTime.class),
                result.getObject("read_at", OffsetDateTime.class),
                result.getObject("resolved_at", OffsetDateTime.class));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("No se pudo serializar el JSON de la alerta.", failure);
        }
    }

    private Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, MAP);
        } catch (JsonProcessingException failure) {
            return null;
        }
    }
}
