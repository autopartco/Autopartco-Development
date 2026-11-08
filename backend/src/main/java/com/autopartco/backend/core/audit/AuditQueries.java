package com.autopartco.backend.core.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/**
 * Lectura del registro de auditoría para administración. El actor se resuelve con {@code accounts} por SQL (sin
 * depender del módulo de identidad). Más recientes primero.
 */
@Repository
@AllArgsConstructor
public class AuditQueries {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    static final String WHERE = """
            FROM audit_logs l
            LEFT JOIN accounts a ON a.id = l.account_id
            WHERE l.deleted_at IS NULL
              AND l.created_at >= :from AND l.created_at < :to
              AND (CAST(:action AS text) IS NULL OR l.action = CAST(:action AS text))
              AND (CAST(:entityType AS text) IS NULL OR l.entity_type = CAST(:entityType AS text))
              AND (CAST(:actor AS text) IS NULL OR a.email ILIKE '%' || CAST(:actor AS text) || '%')
            """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public record Filter(OffsetDateTime from, OffsetDateTime to, String action, String entityType, String actor,
                         int page, int size) {
    }

    public record Actor(UUID accountId, String email) {
    }

    public record Entry(UUID id, OffsetDateTime occurredAt, String action, Actor actor, String entityType,
                        UUID entityId, String ipAddress, Map<String, Object> metadata) {
    }

    public record Page(List<Entry> items, int page, int size, long total) {
    }

    public record Report(Page page) {
    }

    public record ActionOption(String action, String entityType) {
    }

    public Report search(Filter filter) {
        int size = Math.clamp(filter.size(), 1, MAX_PAGE_SIZE);
        int page = Math.max(1, filter.page());
        long total = bind(jdbcClient.sql("SELECT count(*) " + WHERE), filter).query(Long.class).single();
        List<Entry> items = bind(jdbcClient.sql("""
                SELECT l.id, l.created_at, l.action, l.account_id, a.email, l.entity_type, l.entity_id,
                       CAST(l.metadata AS text) AS metadata
                """ + WHERE + " ORDER BY l.created_at DESC, l.id LIMIT :limit OFFSET :offset"), filter)
                .param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query((result, row) -> {
                    Map<String, Object> metadata = parse(result.getString("metadata"));
                    Object ip = metadata.get("ipAddress");
                    return new Entry(
                            result.getObject("id", UUID.class),
                            result.getObject("created_at", OffsetDateTime.class),
                            result.getString("action"),
                            new Actor(result.getObject("account_id", UUID.class), result.getString("email")),
                            result.getString("entity_type"),
                            result.getObject("entity_id", UUID.class),
                            ip == null ? null : ip.toString(),
                            metadata);
                })
                .list();
        return new Report(new Page(items, page, size, total));
    }

    /** Acciones registradas alguna vez, para los filtros. */
    public List<ActionOption> actions() {
        return jdbcClient.sql("""
                        SELECT DISTINCT action, entity_type FROM audit_logs
                        WHERE deleted_at IS NULL ORDER BY action
                        """)
                .query((result, row) -> new ActionOption(result.getString("action"), result.getString("entity_type")))
                .list();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter filter) {
        return spec.param("from", filter.from())
                .param("to", filter.to())
                .param("action", blankToNull(filter.action()))
                .param("entityType", blankToNull(filter.entityType()))
                .param("actor", blankToNull(filter.actor()));
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (Exception malformed) {
            return Map.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
