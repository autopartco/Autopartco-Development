package com.autopartco.backend.marketplace.reviews;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.autopartco.backend.core.reviews.PortalReviewSummary;

import lombok.AllArgsConstructor;

/**
 * Lecturas y escrituras de gestión (portal y administración) sobre {@code store_reviews} y {@code product_reviews}:
 * listado unificado con las ocultas, respuestas y moderación. Los nombres de tabla salen de {@link ReviewKind}
 * (constantes de código). Las fechas se leen como {@code timestamptz}. No comprueba permisos.
 */
@Repository
@AllArgsConstructor
class ReviewDeskQueries {
    static final int TOP = 5;

    /**
     * Filtros del listado. {@code storeId} {@code null} = todos los almacenes (administración); {@code kind}
     * {@code null} = ambos tipos; {@code hidden} {@code null} = con y sin ocultas; {@code replied} {@code null} = con y
     * sin respuesta. Las borradas ({@code deleted_at}) nunca salen.
     */
    record Filter(UUID storeId, ReviewKind kind, UUID id, Boolean replied, Integer stars, Boolean hidden) {
    }

    record DeskRow(ReviewKind kind, UUID id, int rating, String comment, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, String reply, OffsetDateTime repliedAt, OffsetDateTime hiddenAt,
            String hiddenReason, UUID storeId, String storeName, UUID productId, String productName, String name,
            String surname) {
        boolean hidden() {
            return hiddenAt != null;
        }
    }

    private static final String COLUMNS = """
            r.id, r.rating, r.comment, r.created_at::timestamptz AS created_at,
                   r.updated_at::timestamptz AS updated_at, r.reply, r.replied_at::timestamptz AS replied_at,
                   r.hidden_at::timestamptz AS hidden_at, r.hidden_reason, r.store_id, s.business_name AS store_name,
            """;

    private final JdbcClient jdbcClient;

    /** Una sola tabla con las mismas columnas; el tipo va como literal. */
    static String branch(ReviewKind kind, Filter filter) {
        boolean product = kind == ReviewKind.PRODUCT;
        StringBuilder sql = new StringBuilder("SELECT '").append(kind.name()).append("' AS kind, ").append(COLUMNS)
                .append(product ? "r.product_id, pr.name AS product_name, " : "NULL::uuid AS product_id, NULL::text AS product_name, ")
                .append("p.name AS person_name, p.surname AS person_surname\nFROM ").append(kind.table())
                .append(" r\nJOIN persons p ON p.id = r.person_id\nJOIN store s ON s.id = r.store_id\n");
        if (product) {
            sql.append("JOIN products pr ON pr.id = r.product_id\n");
        }
        sql.append("WHERE r.deleted_at IS NULL");
        if (filter.storeId() != null) {
            sql.append(" AND r.store_id = :storeId");
        }
        if (filter.id() != null) {
            sql.append(" AND r.id = :id");
        }
        return sql.toString();
    }

    static String unionSql(Filter filter) {
        List<String> branches = new ArrayList<>();
        for (ReviewKind kind : ReviewKind.values()) {
            if (filter.kind() == null || filter.kind() == kind) {
                branches.add(branch(kind, filter));
            }
        }
        return String.join("\nUNION ALL\n", branches);
    }

    static String whereSql(Filter filter) {
        StringBuilder where = new StringBuilder("WHERE true");
        if (filter.hidden() != null) {
            where.append(filter.hidden() ? " AND x.hidden_at IS NOT NULL" : " AND x.hidden_at IS NULL");
        }
        if (filter.replied() != null) {
            where.append(filter.replied() ? " AND x.reply IS NOT NULL" : " AND x.reply IS NULL");
        }
        if (filter.stars() != null) {
            where.append(" AND x.rating = :stars");
        }
        return where.toString();
    }

    static String listSql(Filter filter) {
        return "SELECT x.* FROM (\n" + unionSql(filter) + "\n) x\n" + whereSql(filter)
                + "\nORDER BY x.created_at DESC, x.id\nLIMIT :limit OFFSET :offset";
    }

    static String countSql(Filter filter) {
        return "SELECT count(*) FROM (\n" + unionSql(filter) + "\n) x\n" + whereSql(filter);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter filter) {
        if (filter.storeId() != null) {
            spec = spec.param("storeId", filter.storeId());
        }
        if (filter.id() != null) {
            spec = spec.param("id", filter.id());
        }
        if (filter.stars() != null) {
            spec = spec.param("stars", filter.stars());
        }
        return spec;
    }

    List<DeskRow> list(Filter filter, int limit, int offset) {
        return bind(jdbcClient.sql(listSql(filter)), filter)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> mapRow(rs))
                .list();
    }

    long count(Filter filter) {
        Long total = bind(jdbcClient.sql(countSql(filter)), filter).query(Long.class).single();
        return total == null ? 0 : total;
    }

    /** Una reseña no borrada del tipo, de ese almacén (si se indica), aunque esté oculta. */
    Optional<DeskRow> find(ReviewKind kind, UUID storeId, UUID reviewId) {
        Filter filter = new Filter(storeId, kind, reviewId, null, null, null);
        return list(filter, 1, 0).stream().findFirst();
    }

    /** Reseñas visibles del almacén (de almacén y de producto) todavía sin respuesta. */
    long unanswered(UUID storeId) {
        Long total = jdbcClient.sql("""
                        SELECT (SELECT count(*) FROM store_reviews
                                WHERE store_id = :storeId AND deleted_at IS NULL AND hidden_at IS NULL AND reply IS NULL)
                             + (SELECT count(*) FROM product_reviews
                                WHERE store_id = :storeId AND deleted_at IS NULL AND hidden_at IS NULL AND reply IS NULL)
                        """)
                .param("storeId", storeId)
                .query(Long.class)
                .single();
        return total == null ? 0 : total;
    }

    /** Productos del almacén con al menos una reseña visible, mejor o peor calificados primero. */
    List<PortalReviewSummary.ProductRating> rankedProducts(UUID storeId, boolean best) {
        String direction = best ? "DESC" : "ASC";
        return jdbcClient.sql("""
                        SELECT r.product_id, pr.name, avg(r.rating) AS average, count(*) AS total
                        FROM product_reviews r
                        JOIN products pr ON pr.id = r.product_id
                        WHERE r.store_id = :storeId AND r.deleted_at IS NULL AND r.hidden_at IS NULL
                        GROUP BY r.product_id, pr.name
                        ORDER BY avg(r.rating) %s, count(*) DESC, pr.name, r.product_id
                        LIMIT :limit
                        """.formatted(direction))
                .param("storeId", storeId)
                .param("limit", TOP)
                .query((rs, row) -> new PortalReviewSummary.ProductRating(
                        rs.getObject("product_id", UUID.class), rs.getString("name"),
                        rs.getBigDecimal("average").setScale(1, RoundingMode.HALF_UP), rs.getLong("total")))
                .list();
    }

    /** Fija o reemplaza la respuesta; {@code false} si la reseña no es de ese almacén o está borrada. */
    boolean setReply(ReviewKind kind, UUID storeId, UUID reviewId, UUID accountId, String text) {
        return jdbcClient.sql("UPDATE " + kind.table() + " SET reply = :text, replied_at = now(),"
                        + " replied_by = :accountId, version = version + 1"
                        + " WHERE id = :id AND store_id = :storeId AND deleted_at IS NULL")
                .param("text", text)
                .param("accountId", accountId)
                .param("id", reviewId)
                .param("storeId", storeId)
                .update() == 1;
    }

    boolean clearReply(ReviewKind kind, UUID storeId, UUID reviewId) {
        return jdbcClient.sql("UPDATE " + kind.table() + " SET reply = NULL, replied_at = NULL, replied_by = NULL,"
                        + " version = version + 1 WHERE id = :id AND store_id = :storeId AND deleted_at IS NULL")
                .param("id", reviewId)
                .param("storeId", storeId)
                .update() == 1;
    }

    void hide(ReviewKind kind, UUID reviewId, String reason) {
        jdbcClient.sql("UPDATE " + kind.table() + " SET hidden_at = now(), hidden_reason = :reason,"
                        + " version = version + 1 WHERE id = :id AND deleted_at IS NULL")
                .param("reason", reason)
                .param("id", reviewId)
                .update();
    }

    void unhide(ReviewKind kind, UUID reviewId) {
        jdbcClient.sql("UPDATE " + kind.table() + " SET hidden_at = NULL, hidden_reason = NULL,"
                        + " version = version + 1 WHERE id = :id AND deleted_at IS NULL")
                .param("id", reviewId)
                .update();
    }

    private static DeskRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeskRow(
                ReviewKind.valueOf(rs.getString("kind")),
                rs.getObject("id", UUID.class),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("reply"),
                rs.getObject("replied_at", OffsetDateTime.class),
                rs.getObject("hidden_at", OffsetDateTime.class),
                rs.getString("hidden_reason"),
                rs.getObject("store_id", UUID.class),
                rs.getString("store_name"),
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("person_name"),
                rs.getString("person_surname"));
    }
}
