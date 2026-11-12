package com.autopartco.backend.marketplace.reviews;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Lecturas y escrituras JDBC comunes a {@code store_reviews} y {@code product_reviews} (y sus tablas de categorías).
 * Lo público excluye siempre las borradas ({@code deleted_at}) y las ocultas ({@code hidden_at}). No comprueba
 * permisos. Las fechas se leen como {@code timestamptz}.
 */
@Repository
@AllArgsConstructor
class ReviewQueries {
    static final String VISIBLE = "r.deleted_at IS NULL AND r.hidden_at IS NULL";

    static final String ROW_COLUMNS = """
            r.id, r.rating, r.comment, r.created_at::timestamptz AS created_at,
                   r.updated_at::timestamptz AS updated_at, r.reply, r.replied_at::timestamptz AS replied_at,
                   p.name, p.surname
            """;

    /** Fila de reseña con el nombre del autor (solo para formar «Ana P.»; nunca se expone tal cual). */
    record ReviewRow(UUID id, int rating, String comment, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String reply, OffsetDateTime repliedAt, String name, String surname) {
    }

    /** Reseña vigente (no borrada) con su dueño y objeto calificado, para editar o borrar. */
    record OwnedReview(UUID id, UUID scopeId, UUID personId, OffsetDateTime createdAt) {
    }

    /** Reseña borrada por su autor que puede reutilizarse al volver a calificar; {@code hidden}: estaba oculta. */
    record Deleted(UUID id, boolean hidden) {
    }

    private final JdbcClient jdbcClient;

    static String listSql(ReviewKind kind, ReviewListQuery query) {
        return "SELECT " + ROW_COLUMNS + "FROM " + kind.table() + " r\nJOIN persons p ON p.id = r.person_id\n"
                + where(kind, query) + "\nORDER BY " + query.sort().orderBy() + "\nLIMIT :limit OFFSET :offset";
    }

    static String countSql(ReviewKind kind, ReviewListQuery query) {
        return "SELECT count(*) FROM " + kind.table() + " r\n" + where(kind, query);
    }

    private static String where(ReviewKind kind, ReviewListQuery query) {
        StringBuilder where = new StringBuilder("WHERE r.").append(kind.scopeColumn()).append(" = :scopeId AND ")
                .append(VISIBLE);
        if (query.stars() != null) {
            where.append(" AND r.rating = :stars");
        }
        if (query.withComment()) {
            where.append(" AND r.comment IS NOT NULL AND btrim(r.comment) <> ''");
        }
        return where.toString();
    }

    static String distributionSql(ReviewKind kind) {
        return "SELECT r.rating, count(*) AS total FROM " + kind.table() + " r WHERE r." + kind.scopeColumn()
                + " = :scopeId AND " + VISIBLE + " GROUP BY r.rating";
    }

    static String aspectStatsSql(ReviewKind kind) {
        return "SELECT a.aspect, avg(a.rating) AS average, count(*) AS total FROM " + kind.aspectTable() + " a\n"
                + "JOIN " + kind.table() + " r ON r.id = a.review_id\n"
                + "WHERE r." + kind.scopeColumn() + " = :scopeId AND " + VISIBLE + "\nGROUP BY a.aspect";
    }

    static String rowSql(ReviewKind kind) {
        return "SELECT " + ROW_COLUMNS + "FROM " + kind.table() + " r\nJOIN persons p ON p.id = r.person_id\n"
                + "WHERE r.id = :id AND r.deleted_at IS NULL";
    }

    List<ReviewRow> list(ReviewKind kind, UUID scopeId, ReviewListQuery query) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(listSql(kind, query))
                .param("scopeId", scopeId)
                .param("limit", query.size())
                .param("offset", query.offset());
        if (query.stars() != null) {
            spec = spec.param("stars", query.stars());
        }
        return spec.query((rs, row) -> mapRow(rs)).list();
    }

    long count(ReviewKind kind, UUID scopeId, ReviewListQuery query) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(countSql(kind, query)).param("scopeId", scopeId);
        if (query.stars() != null) {
            spec = spec.param("stars", query.stars());
        }
        Long total = spec.query(Long.class).single();
        return total == null ? 0 : total;
    }

    /** Reseñas visibles por estrellas. */
    Map<Integer, Long> distribution(ReviewKind kind, UUID scopeId) {
        Map<Integer, Long> result = new HashMap<>();
        jdbcClient.sql(distributionSql(kind)).param("scopeId", scopeId)
                .query((rs, row) -> result.put(rs.getInt("rating"), rs.getLong("total")))
                .list();
        return result;
    }

    List<ReviewSummary.AspectStat> aspectStats(ReviewKind kind, UUID scopeId) {
        return jdbcClient.sql(aspectStatsSql(kind)).param("scopeId", scopeId)
                .query((rs, row) -> new ReviewSummary.AspectStat(rs.getString("aspect"),
                        rs.getBigDecimal("average"), rs.getLong("total")))
                .list();
    }

    ReviewSummary summary(ReviewKind kind, UUID scopeId) {
        return ReviewSummary.of(distribution(kind, scopeId), aspectStats(kind, scopeId), kind.aspects());
    }

    /** Categorías de las reseñas pedidas: id → (código → nota), en el orden del enum. */
    Map<UUID, Map<String, Integer>> aspectsOf(ReviewKind kind, Collection<UUID> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Integer>> raw = new HashMap<>();
        jdbcClient.sql("SELECT review_id, aspect, rating FROM " + kind.aspectTable() + " WHERE review_id IN (:ids)")
                .param("ids", List.copyOf(reviewIds))
                .query((rs, row) -> raw.computeIfAbsent(rs.getObject("review_id", UUID.class), id -> new HashMap<>())
                        .put(rs.getString("aspect"), rs.getInt("rating")))
                .list();
        Map<UUID, Map<String, Integer>> result = new HashMap<>();
        raw.forEach((id, aspects) -> {
            Map<String, Integer> ordered = new LinkedHashMap<>();
            kind.aspects().forEach(code -> {
                if (aspects.containsKey(code)) {
                    ordered.put(code, aspects.get(code));
                }
            });
            result.put(id, ordered);
        });
        return result;
    }

    Map<String, Integer> aspectsOf(ReviewKind kind, UUID reviewId) {
        return aspectsOf(kind, List.of(reviewId)).getOrDefault(reviewId, Map.of());
    }

    /** Reemplaza las categorías de la reseña ({@code aspects} ya validadas). */
    void replaceAspects(ReviewKind kind, UUID reviewId, Map<String, Integer> aspects) {
        jdbcClient.sql("DELETE FROM " + kind.aspectTable() + " WHERE review_id = :id").param("id", reviewId).update();
        for (Map.Entry<String, Integer> aspect : aspects.entrySet()) {
            jdbcClient.sql("INSERT INTO " + kind.aspectTable() + " (review_id, aspect, rating) VALUES (:id, :aspect, :rating)")
                    .param("id", reviewId)
                    .param("aspect", aspect.getKey())
                    .param("rating", aspect.getValue())
                    .update();
        }
    }

    /** Reseña no borrada (aunque esté oculta), con autor y nombre, para responder tras escribir. */
    Optional<ReviewRow> row(ReviewKind kind, UUID reviewId) {
        return jdbcClient.sql(rowSql(kind)).param("id", reviewId).query((rs, row) -> mapRow(rs)).optional();
    }

    Optional<OwnedReview> owned(ReviewKind kind, UUID reviewId) {
        return jdbcClient.sql("SELECT r.id, r." + kind.scopeColumn() + " AS scope_id, r.person_id, "
                        + "r.created_at::timestamptz AS created_at FROM " + kind.table()
                        + " r WHERE r.id = :id AND r.deleted_at IS NULL")
                .param("id", reviewId)
                .query((rs, row) -> new OwnedReview(rs.getObject("id", UUID.class),
                        rs.getObject("scope_id", UUID.class), rs.getObject("person_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    void update(ReviewKind kind, UUID reviewId, int rating, String comment) {
        // Las dos tablas (store_reviews y product_reviews) tienen `version`.
        jdbcClient.sql("UPDATE " + kind.table() + " SET rating = :rating, comment = :comment, updated_at = now(),"
                        + " version = version + 1 WHERE id = :id AND deleted_at IS NULL")
                .param("rating", rating)
                .param("comment", comment)
                .param("id", reviewId)
                .update();
    }

    /**
     * Reutiliza una reseña borrada y no oculta como si fuera nueva: valores nuevos, sin respuesta del almacén y con
     * {@code created_at} reiniciado (abre de nuevo la ventana de edición). Las categorías se reemplazan aparte.
     *
     * @return {@code false} si ya no está borrada o fue ocultada entre la lectura y la escritura
     * @throws org.springframework.dao.DuplicateKeyException si otra petición creó una reseña vigente
     */
    boolean restore(ReviewKind kind, UUID reviewId, UUID orderId, int rating, String comment) {
        return jdbcClient.sql("UPDATE " + kind.table() + " SET deleted_at = NULL, order_id = :orderId,"
                        + " rating = :rating, comment = :comment, created_at = now(), updated_at = now(),"
                        + " version = version + 1, reply = NULL, replied_at = NULL, replied_by = NULL"
                        + " WHERE id = :id AND deleted_at IS NOT NULL AND hidden_at IS NULL")
                .param("orderId", orderId)
                .param("rating", rating)
                .param("comment", comment)
                .param("id", reviewId)
                .update() == 1;
    }

    void softDelete(ReviewKind kind, UUID reviewId) {
        jdbcClient.sql("UPDATE " + kind.table() + " SET deleted_at = now(), updated_at = now()"
                        + " WHERE id = :id AND deleted_at IS NULL")
                .param("id", reviewId)
                .update();
    }

    private static ReviewRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewRow(
                rs.getObject("id", UUID.class),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("reply"),
                rs.getObject("replied_at", OffsetDateTime.class),
                rs.getString("name"),
                rs.getString("surname"));
    }

    static List<UUID> ids(List<ReviewRow> rows) {
        List<UUID> ids = new ArrayList<>(rows.size());
        rows.forEach(row -> ids.add(row.id()));
        return ids;
    }
}
