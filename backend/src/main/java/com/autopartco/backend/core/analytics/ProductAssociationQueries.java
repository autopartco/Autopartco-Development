package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.AllArgsConstructor;

/**
 * Lecturas y escrituras de {@code product_associations} y de los datos que la alimentan (ventas válidas por pedido,
 * {@code products}, {@code inventory}, reglas de vehículo). Solo SQL directo: no depende de otros módulos.
 */
@Repository
@AllArgsConstructor
class ProductAssociationQueries {
    static final String ORDERS = "ORDERS";
    static final String COMPATIBILITY = "COMPATIBILITY";
    static final String CATEGORY = "CATEGORY";

    /** Una línea de canasta: el producto vendido en un pedido, en un almacén. */
    static final String BASKET_LINES_SQL = """
            SELECT DISTINCT s.order_id, b.store_id, d.product_id
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            WHERE s.deleted_at IS NULL
              AND st.code IN %s
              AND s.created_at >= :since
              AND d.product_id IS NOT NULL
            """.formatted(Analytics.VALID_SALE_STATES);

    /** Almacenes con productos o con asociaciones guardadas (para reescribir o limpiar su ámbito). */
    static final String SCOPE_STORES_SQL = """
            SELECT store_id FROM products WHERE store_id IS NOT NULL AND deleted_at IS NULL
            UNION
            SELECT store_id FROM product_associations WHERE store_id IS NOT NULL
            """;

    static final String DELETE_SQL = """
            DELETE FROM product_associations WHERE store_id IS NOT DISTINCT FROM CAST(:storeId AS uuid)
            """;

    static final String INSERT_SQL = """
            INSERT INTO product_associations
                (store_id, antecedent_product_id, consequent_product_id, support, confidence, lift, source, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT DO NOTHING
            """;

    /**
     * Productos publicados con stock, como en el marketplace: producto, fabricante, categoría, almacén y sucursal
     * activos y sin borrar, con inventario con stock. {@code :storeId} opcional restringe al almacén.
     */
    static final String PUBLISHED_CTE = """
            published AS (
                SELECT DISTINCT p.id, p.category_id, p.created_at
                FROM products p
                JOIN states ps ON ps.id = p.state_id AND ps.deleted_at IS NULL
                JOIN manufacturers m ON m.id = p.manufacturer_id AND m.deleted_at IS NULL
                JOIN categories c ON c.id = p.category_id AND c.deleted_at IS NULL
                JOIN inventory i ON i.product_id = p.id AND i.deleted_at IS NULL AND i.stock > 0
                JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
                JOIN states bs ON bs.id = b.state AND bs.deleted_at IS NULL
                JOIN store s ON s.id = b.store_id AND s.deleted_at IS NULL
                JOIN states ss ON ss.id = s.state AND ss.deleted_at IS NULL
                WHERE p.deleted_at IS NULL
                  AND ps.code = 'ACTIVE' AND bs.code = 'ACTIVE' AND ss.code = 'ACTIVE'
                  AND (CAST(:storeId AS uuid) IS NULL OR p.store_id = CAST(:storeId AS uuid))
            )
            """;

    private static final String COMPATIBLE_CONDITION = """
            EXISTS (
                SELECT 1
                FROM product_vehicle_rules r1
                JOIN product_vehicle_rules r2
                  ON r2.make_id = r1.make_id
                 AND (r1.model_id IS NULL OR r2.model_id IS NULL OR r1.model_id = r2.model_id)
                 AND (r1.version_id IS NULL OR r2.version_id IS NULL OR r1.version_id = r2.version_id)
                WHERE r1.product_id = a.id AND r2.product_id = c.id
                  AND r1.deleted_at IS NULL AND r2.deleted_at IS NULL)
            """;

    private static final String SAME_CATEGORY_CONDITION = "c.category_id = a.category_id";

    /**
     * Respaldo sin volumen: por antecedente (activo, del almacén y, si se pide, un solo producto), hasta
     * {@code :limit} consecuentes publicados con stock que cumplen la condición, los más vendidos (365 días) y
     * recientes primero. {@code :storeId} y {@code :productId} opcionales.
     */
    private static final String FALLBACK_TEMPLATE = """
            WITH %s,
            sold AS (
                SELECT d.product_id, sum(d.units) AS units
                FROM order_details d
                JOIN sales s ON s.id = d.sale_id
                JOIN states st ON st.id = s.state_id
                WHERE s.deleted_at IS NULL AND st.code IN %s AND s.created_at >= :since AND d.product_id IS NOT NULL
                GROUP BY d.product_id
            )
            SELECT ranked.antecedent, ranked.consequent
            FROM (
                SELECT a.id AS antecedent, c.id AS consequent,
                       row_number() OVER (PARTITION BY a.id
                           ORDER BY coalesce(sold.units, 0) DESC, c.created_at DESC, c.id) AS rn
                FROM products a
                JOIN states pas ON pas.id = a.state_id AND pas.deleted_at IS NULL AND pas.code = 'ACTIVE'
                JOIN published c ON c.id <> a.id AND %s
                LEFT JOIN sold ON sold.product_id = c.id
                WHERE a.deleted_at IS NULL
                  AND (CAST(:storeId AS uuid) IS NULL OR a.store_id = CAST(:storeId AS uuid))
                  AND (CAST(:productId AS uuid) IS NULL OR a.id = CAST(:productId AS uuid))
            ) ranked
            WHERE ranked.rn <= :limit
            ORDER BY ranked.antecedent, ranked.rn
            """;

    static final String COMPATIBILITY_SQL = FALLBACK_TEMPLATE.formatted(
            PUBLISHED_CTE, Analytics.VALID_SALE_STATES, COMPATIBLE_CONDITION);
    static final String CATEGORY_SQL = FALLBACK_TEMPLATE.formatted(
            PUBLISHED_CTE, Analytics.VALID_SALE_STATES, SAME_CATEGORY_CONDITION);

    /**
     * Filas guardadas del producto como antecedente hacia productos publicados con stock, del almacén indicado
     * (si se da) y globales. Orden de preferencia: almacén antes que global, ORDERS antes que respaldos, lift y
     * confianza altos primero.
     */
    static final String RELATED_SQL = """
            WITH %s
            SELECT a.consequent_product_id, a.source
            FROM product_associations a
            JOIN published c ON c.id = a.consequent_product_id
            WHERE a.antecedent_product_id = :productId
              AND (a.store_id IS NULL OR a.store_id = CAST(:storeId AS uuid))
            ORDER BY (a.store_id IS NULL),
                     CASE a.source WHEN 'ORDERS' THEN 0 WHEN 'COMPATIBILITY' THEN 1 ELSE 2 END,
                     a.lift DESC, a.confidence DESC, a.consequent_product_id
            LIMIT :limit
            """.formatted(PUBLISHED_CTE.replace("(CAST(:storeId AS uuid) IS NULL OR p.store_id = CAST(:storeId AS uuid))",
                    "true"));

    static final String PRODUCT_STORE_SQL = """
            SELECT store_id FROM products WHERE id = :productId AND deleted_at IS NULL
            """;

    static final String STORE_PAIRS_SQL = """
            SELECT a.antecedent_product_id, pa.name AS antecedent_name,
                   a.consequent_product_id, pc.name AS consequent_name,
                   a.support, a.confidence, a.lift, a.source, a.computed_at
            FROM product_associations a
            JOIN products pa ON pa.id = a.antecedent_product_id AND pa.deleted_at IS NULL
            JOIN products pc ON pc.id = a.consequent_product_id AND pc.deleted_at IS NULL
            WHERE a.store_id = :storeId
            ORDER BY a.lift DESC, a.confidence DESC, pa.name, pc.name
            """;

    static final String STORE_ORDERS_SQL = """
            SELECT count(DISTINCT s.order_id)
            FROM sales s
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            WHERE s.deleted_at IS NULL
              AND st.code IN %s
              AND s.created_at >= :since
              AND b.store_id = :storeId
            """.formatted(Analytics.VALID_SALE_STATES);

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    record BasketLine(UUID orderId, UUID storeId, UUID productId) {
    }

    /** Par guardado o por guardar. */
    record Association(UUID antecedent, UUID consequent, BigDecimal support, BigDecimal confidence, BigDecimal lift,
            String source) {
    }

    record Link(UUID antecedent, UUID consequent) {
    }

    record RelatedRow(UUID consequent, String source) {
    }

    record StoredPair(UUID antecedentId, String antecedentName, UUID consequentId, String consequentName,
            BigDecimal support, BigDecimal confidence, BigDecimal lift, String source, OffsetDateTime computedAt) {
    }

    List<BasketLine> basketLines(OffsetDateTime since) {
        return jdbcClient.sql(BASKET_LINES_SQL).param("since", since)
                .query((rs, row) -> new BasketLine(
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("store_id", UUID.class),
                        rs.getObject("product_id", UUID.class)))
                .list();
    }

    List<UUID> scopeStores() {
        return jdbcClient.sql(SCOPE_STORES_SQL).query((rs, row) -> rs.getObject("store_id", UUID.class)).list();
    }

    /** Respaldo por compatibilidad de vehículo; {@code storeId} y {@code productId} opcionales. */
    List<Link> compatible(UUID storeId, UUID productId, int limit, OffsetDateTime since) {
        return fallback(COMPATIBILITY_SQL, storeId, productId, limit, since);
    }

    /** Respaldo por misma categoría; {@code storeId} y {@code productId} opcionales. */
    List<Link> sameCategory(UUID storeId, UUID productId, int limit, OffsetDateTime since) {
        return fallback(CATEGORY_SQL, storeId, productId, limit, since);
    }

    private List<Link> fallback(String sql, UUID storeId, UUID productId, int limit, OffsetDateTime since) {
        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("productId", productId)
                .param("limit", limit)
                .param("since", since)
                .query((rs, row) -> new Link(
                        rs.getObject("antecedent", UUID.class), rs.getObject("consequent", UUID.class)))
                .list();
    }

    /** Borra el ámbito ({@code null} = global). Se llama dentro de la transacción del ámbito. */
    void deleteScope(UUID storeId) {
        jdbcClient.sql(DELETE_SQL).param("storeId", storeId).update();
    }

    /** Inserta por lotes; un par ya existente en el ámbito se conserva. */
    void insert(UUID storeId, List<Association> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (statement, row) -> {
            if (storeId == null) {
                statement.setNull(1, Types.OTHER);
            } else {
                statement.setObject(1, storeId);
            }
            statement.setObject(2, row.antecedent());
            statement.setObject(3, row.consequent());
            statement.setBigDecimal(4, row.support());
            statement.setBigDecimal(5, row.confidence());
            statement.setBigDecimal(6, row.lift());
            statement.setString(7, row.source());
        });
    }

    UUID productStore(UUID productId) {
        return jdbcClient.sql(PRODUCT_STORE_SQL).param("productId", productId)
                .query((rs, row) -> rs.getObject("store_id", UUID.class))
                .optional().orElse(null);
    }

    List<RelatedRow> related(UUID productId, UUID storeId, int limit) {
        return jdbcClient.sql(RELATED_SQL)
                .param("productId", productId)
                .param("storeId", storeId)
                .param("limit", limit)
                .query((rs, row) -> new RelatedRow(rs.getObject("consequent_product_id", UUID.class),
                        rs.getString("source")))
                .list();
    }

    List<StoredPair> storePairs(UUID storeId) {
        return jdbcClient.sql(STORE_PAIRS_SQL).param("storeId", storeId)
                .query((rs, row) -> new StoredPair(
                        rs.getObject("antecedent_product_id", UUID.class), rs.getString("antecedent_name"),
                        rs.getObject("consequent_product_id", UUID.class), rs.getString("consequent_name"),
                        rs.getBigDecimal("support"), rs.getBigDecimal("confidence"), rs.getBigDecimal("lift"),
                        rs.getString("source"), rs.getObject("computed_at", OffsetDateTime.class)))
                .list();
    }

    long storeOrders(UUID storeId, OffsetDateTime since) {
        return jdbcClient.sql(STORE_ORDERS_SQL).param("storeId", storeId).param("since", since)
                .query(Long.class).single();
    }
}
