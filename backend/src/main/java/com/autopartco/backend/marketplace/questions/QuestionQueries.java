package com.autopartco.backend.marketplace.questions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Lecturas y escrituras JDBC sobre {@code product_questions}. Las borradas ({@code deleted_at}) nunca salen; lo
 * público excluye además las ocultas. No comprueba permisos. Las fechas se leen como {@code timestamptz}.
 */
@Repository
@AllArgsConstructor
class QuestionQueries {

    /** Fila de pregunta con producto, almacén y nombre del autor (solo para formar «Ana P.»; nunca se expone tal cual). */
    record QuestionRow(UUID id, UUID productId, String productName, UUID storeId, String storeName, UUID personId,
            String question, String answer, OffsetDateTime answeredAt, OffsetDateTime createdAt,
            OffsetDateTime hiddenAt, String hiddenReason, String name, String surname) {
        boolean hidden() {
            return hiddenAt != null;
        }

        boolean answered() {
            return answer != null;
        }
    }

    /**
     * Filtros de gestión (portal y administración). {@code storeId} {@code null} = todos los almacenes;
     * {@code answered}/{@code hidden} {@code null} = sin filtrar; {@code branchIds} {@code null} = todo el almacén,
     * si no, productos con inventario en alguna de esas sucursales del almacén (vacío = nada).
     */
    record Filter(UUID storeId, UUID id, Boolean answered, Boolean hidden, Collection<UUID> branchIds) {
    }

    /** Autor de una pregunta: su cuenta, correo y nombre de pila (para avisarle). */
    record Author(UUID accountId, String email, String name) {
    }

    static final String SELECT = """
            SELECT q.id, q.product_id, pr.name AS product_name, q.store_id, s.business_name AS store_name,
                   q.person_id, q.question, q.answer, q.answered_at::timestamptz AS answered_at,
                   q.created_at::timestamptz AS created_at, q.hidden_at::timestamptz AS hidden_at, q.hidden_reason,
                   p.name AS person_name, p.surname AS person_surname
            FROM product_questions q
            JOIN persons p ON p.id = q.person_id
            JOIN products pr ON pr.id = q.product_id
            JOIN store s ON s.id = q.store_id
            """;

    static final String PRODUCT_STORE_SQL = """
            SELECT store_id FROM products WHERE id = :productId AND deleted_at IS NULL AND store_id IS NOT NULL
            """;

    static final String INSERT_SQL = """
            INSERT INTO product_questions (product_id, store_id, person_id, question)
            VALUES (:productId, :storeId, :personId, :question)
            RETURNING id
            """;

    /**
     * Cuentas que se enteran de una pregunta nueva: el dueño del almacén y los colaboradores activos asignados a una
     * sucursal con inventario del producto. Una fila por cuenta; sin el propio autor.
     */
    static final String STORE_RECIPIENTS_SQL = """
            SELECT DISTINCT a.id
            FROM collaborators c
            JOIN states st ON st.id = c.state AND st.code = 'ACTIVE'
            JOIN accounts a ON a.id_person = c.person_id AND a.deleted_at IS NULL
            WHERE c.collab_store_id = :storeId AND c.deleted_at IS NULL AND c.person_id <> :authorPersonId
              AND (
                  EXISTS (SELECT 1 FROM roles_collaborators rc
                          JOIN roles r ON r.id = rc.role_id AND r.deleted_at IS NULL
                          WHERE rc.collaborator_id = c.id AND rc.deleted_at IS NULL AND r.code = 'OWNER')
                  OR EXISTS (SELECT 1 FROM branches_collaborators bc
                             JOIN branches b ON b.id = bc.branch_id AND b.deleted_at IS NULL AND b.store_id = :storeId
                             JOIN inventory i ON i.branch_id = b.id AND i.product_id = :productId
                                             AND i.deleted_at IS NULL
                             WHERE bc.collaborator_id = c.id AND bc.deleted_at IS NULL)
              )
            ORDER BY a.id
            """;

    static final String AUTHOR_SQL = """
            SELECT a.id, a.email, p.name
            FROM accounts a
            JOIN persons p ON p.id = a.id_person
            WHERE a.id_person = :personId AND a.deleted_at IS NULL
            ORDER BY a.created_at, a.id
            LIMIT 1
            """;

    private final JdbcClient jdbcClient;

    /** Almacén del producto no borrado; vacío si no existe. */
    Optional<UUID> productStore(UUID productId) {
        return jdbcClient.sql(PRODUCT_STORE_SQL).param("productId", productId).query(UUID.class).optional();
    }

    // ---------------------------------------------------------------- público

    /**
     * Visibles del producto: respondidas y, si {@code viewerPersonId} no es {@code null}, también las pendientes de esa
     * persona. {@code pattern}: ILIKE sobre pregunta y respuesta, o {@code null}.
     */
    static String publicWhere(boolean withViewer, boolean withPattern) {
        StringBuilder where = new StringBuilder(
                "WHERE q.product_id = :productId AND q.deleted_at IS NULL AND q.hidden_at IS NULL");
        where.append(withViewer ? " AND (q.answer IS NOT NULL OR q.person_id = :viewer)" : " AND q.answer IS NOT NULL");
        if (withPattern) {
            where.append(" AND (q.question ILIKE :pattern OR q.answer ILIKE :pattern)");
        }
        return where.toString();
    }

    static String publicListSql(boolean withViewer, boolean withPattern) {
        return SELECT + publicWhere(withViewer, withPattern)
                + "\nORDER BY q.answered_at DESC NULLS LAST, q.created_at DESC, q.id\nLIMIT :limit OFFSET :offset";
    }

    static String publicCountSql(boolean withViewer, boolean withPattern) {
        return "SELECT count(*) FROM product_questions q\n" + publicWhere(withViewer, withPattern);
    }

    private static JdbcClient.StatementSpec bindPublic(JdbcClient.StatementSpec spec, UUID productId, UUID viewer,
            String pattern) {
        spec = spec.param("productId", productId);
        if (viewer != null) {
            spec = spec.param("viewer", viewer);
        }
        if (pattern != null) {
            spec = spec.param("pattern", pattern);
        }
        return spec;
    }

    List<QuestionRow> publicList(UUID productId, UUID viewer, String pattern, int limit, int offset) {
        return bindPublic(jdbcClient.sql(publicListSql(viewer != null, pattern != null)), productId, viewer, pattern)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> mapRow(rs))
                .list();
    }

    long publicCount(UUID productId, UUID viewer, String pattern) {
        Long total = bindPublic(jdbcClient.sql(publicCountSql(viewer != null, pattern != null)), productId, viewer,
                pattern).query(Long.class).single();
        return total == null ? 0 : total;
    }

    UUID insert(UUID productId, UUID storeId, UUID personId, String question) {
        return jdbcClient.sql(INSERT_SQL)
                .param("productId", productId)
                .param("storeId", storeId)
                .param("personId", personId)
                .param("question", question)
                .query(UUID.class)
                .single();
    }

    /** Pregunta no borrada (aunque esté oculta), de cualquier almacén. */
    Optional<QuestionRow> find(UUID questionId) {
        return list(new Filter(null, questionId, null, null, null), 1, 0).stream().findFirst();
    }

    /** Borrado lógico por el autor, solo si sigue sin respuesta. {@code false} si no se borró. */
    boolean softDeleteUnanswered(UUID questionId, UUID personId) {
        return jdbcClient.sql("""
                        UPDATE product_questions SET deleted_at = now(), updated_at = now(), version = version + 1
                        WHERE id = :id AND person_id = :personId AND deleted_at IS NULL AND answer IS NULL
                        """)
                .param("id", questionId)
                .param("personId", personId)
                .update() == 1;
    }

    // ---------------------------------------------------------------- gestión

    static String where(Filter filter) {
        StringBuilder where = new StringBuilder("WHERE q.deleted_at IS NULL");
        if (filter.storeId() != null) {
            where.append(" AND q.store_id = :storeId");
        }
        if (filter.id() != null) {
            where.append(" AND q.id = :id");
        }
        if (filter.answered() != null) {
            where.append(filter.answered() ? " AND q.answer IS NOT NULL" : " AND q.answer IS NULL");
        }
        if (filter.hidden() != null) {
            where.append(filter.hidden() ? " AND q.hidden_at IS NOT NULL" : " AND q.hidden_at IS NULL");
        }
        if (filter.branchIds() != null) {
            where.append(filter.branchIds().isEmpty() ? " AND false" : """
                     AND EXISTS (SELECT 1 FROM inventory i
                                JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL AND b.store_id = q.store_id
                                WHERE i.product_id = q.product_id AND i.deleted_at IS NULL AND i.branch_id IN (:branchIds))""");
        }
        return where.toString();
    }

    static String listSql(Filter filter) {
        return SELECT + where(filter) + "\nORDER BY q.created_at DESC, q.id\nLIMIT :limit OFFSET :offset";
    }

    static String countSql(Filter filter) {
        return "SELECT count(*) FROM product_questions q\n" + where(filter);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter filter) {
        if (filter.storeId() != null) {
            spec = spec.param("storeId", filter.storeId());
        }
        if (filter.id() != null) {
            spec = spec.param("id", filter.id());
        }
        if (filter.branchIds() != null && !filter.branchIds().isEmpty()) {
            spec = spec.param("branchIds", List.copyOf(filter.branchIds()));
        }
        return spec;
    }

    List<QuestionRow> list(Filter filter, int limit, int offset) {
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

    /** Pregunta no borrada del almacén y dentro del alcance de sucursales ({@code null} = todo el almacén). */
    Optional<QuestionRow> findInStore(UUID storeId, UUID questionId, Collection<UUID> branchIds) {
        return list(new Filter(storeId, questionId, null, null, branchIds), 1, 0).stream().findFirst();
    }

    /** Fija o reemplaza la respuesta; {@code false} si la pregunta no es de ese almacén o está borrada. */
    boolean setAnswer(UUID storeId, UUID questionId, UUID accountId, String text) {
        return jdbcClient.sql("""
                        UPDATE product_questions
                        SET answer = :text, answered_at = now(), answered_by = :accountId, updated_at = now(),
                            version = version + 1
                        WHERE id = :id AND store_id = :storeId AND deleted_at IS NULL
                        """)
                .param("text", text)
                .param("accountId", accountId)
                .param("id", questionId)
                .param("storeId", storeId)
                .update() == 1;
    }

    boolean clearAnswer(UUID storeId, UUID questionId) {
        return jdbcClient.sql("""
                        UPDATE product_questions
                        SET answer = NULL, answered_at = NULL, answered_by = NULL, updated_at = now(),
                            version = version + 1
                        WHERE id = :id AND store_id = :storeId AND deleted_at IS NULL
                        """)
                .param("id", questionId)
                .param("storeId", storeId)
                .update() == 1;
    }

    void hide(UUID questionId, String reason) {
        jdbcClient.sql("""
                        UPDATE product_questions SET hidden_at = now(), hidden_reason = :reason, updated_at = now(),
                            version = version + 1
                        WHERE id = :id AND deleted_at IS NULL
                        """)
                .param("reason", reason)
                .param("id", questionId)
                .update();
    }

    void unhide(UUID questionId) {
        jdbcClient.sql("""
                        UPDATE product_questions SET hidden_at = NULL, hidden_reason = NULL, updated_at = now(),
                            version = version + 1
                        WHERE id = :id AND deleted_at IS NULL
                        """)
                .param("id", questionId)
                .update();
    }

    // ---------------------------------------------------------------- avisos

    List<UUID> storeRecipients(UUID storeId, UUID productId, UUID authorPersonId) {
        return jdbcClient.sql(STORE_RECIPIENTS_SQL)
                .param("storeId", storeId)
                .param("productId", productId)
                .param("authorPersonId", authorPersonId)
                .query(UUID.class)
                .list();
    }

    /** Correo del almacén ({@code business_email}), para cuando no configuró destinatarios. */
    Optional<String> storeEmail(UUID storeId) {
        return jdbcClient.sql("SELECT business_email FROM store WHERE id = :storeId AND deleted_at IS NULL")
                .param("storeId", storeId)
                .query(String.class)
                .optional();
    }

    Optional<Author> author(UUID personId) {
        return jdbcClient.sql(AUTHOR_SQL)
                .param("personId", personId)
                .query((rs, row) -> new Author(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("name")))
                .optional();
    }

    private static QuestionRow mapRow(ResultSet rs) throws SQLException {
        return new QuestionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getObject("store_id", UUID.class),
                rs.getString("store_name"),
                rs.getObject("person_id", UUID.class),
                rs.getString("question"),
                rs.getString("answer"),
                rs.getObject("answered_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("hidden_at", OffsetDateTime.class),
                rs.getString("hidden_reason"),
                rs.getString("person_name"),
                rs.getString("person_surname"));
    }
}
