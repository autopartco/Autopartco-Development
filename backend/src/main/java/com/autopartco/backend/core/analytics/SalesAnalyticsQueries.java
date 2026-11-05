package com.autopartco.backend.core.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Lecturas JDBC de ventas de un almacén para la analítica (read model sobre {@code sales}, {@code order_details},
 * {@code orders}, {@code states}, {@code branches}, {@code products}, {@code categories}, {@code inventory} e
 * {@code inventory_reorder_policies}). Sucursal opcional; no comprueba permisos.
 */
@Repository
@AllArgsConstructor
class SalesAnalyticsQueries {
    /** Default del umbral de stock bajo sin política vigente (igual que {@code core.inventory}). */
    static final int DEFAULT_MIN_STOCK = 3;

    private static final String VALID = "st.code IN " + Analytics.VALID_SALE_STATES;
    private static final String LINE_REVENUE = "(d.units * d.unit_price - coalesce(d.discount_amount, 0))";

    private static final String SALE_JOINS = """
            FROM sales s
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            """;

    private static final String SALE_FILTER = """
            WHERE b.store_id = :storeId
              AND s.deleted_at IS NULL
              AND (CAST(:branchId AS uuid) IS NULL OR s.branch_id = CAST(:branchId AS uuid))
              AND s.created_at >= :fromTs AND s.created_at < :toTs
            """;

    private static final String SALE_UNITS = """
            LEFT JOIN LATERAL (
                SELECT sum(d.units) AS units FROM order_details d WHERE d.sale_id = s.id
            ) u ON true
            """;

    private static final String LINE_JOINS = """
            FROM order_details d
            JOIN sales s ON s.id = d.sale_id
            JOIN branches b ON b.id = s.branch_id
            JOIN states st ON st.id = s.state_id
            LEFT JOIN products p ON p.id = d.product_id
            """;

    static final String TOTALS_SQL = """
            SELECT count(*) FILTER (WHERE %1$s) AS orders,
                   coalesce(sum(s.total) FILTER (WHERE %1$s), 0) AS revenue,
                   coalesce(sum(coalesce(s.discount_total, 0)) FILTER (WHERE %1$s), 0) AS discounts,
                   count(DISTINCT o.person_id) FILTER (WHERE %1$s) AS customers,
                   coalesce(sum(u.units) FILTER (WHERE %1$s), 0) AS units,
                   count(*) FILTER (
                       WHERE st.code = 'CANCELLED'
                         AND o.cancellation_reason_code IS DISTINCT FROM 'PAYMENT_TIMEOUT'
                   ) AS cancelled_orders
            """.formatted(VALID) + SALE_JOINS + """
            JOIN orders o ON o.id = s.order_id
            """ + SALE_UNITS + SALE_FILTER;

    static final String DAILY_SQL = """
            SELECT (s.created_at AT TIME ZONE 'America/Bogota')::date AS day,
                   count(*) AS orders,
                   coalesce(sum(s.total), 0) AS revenue,
                   coalesce(sum(u.units), 0) AS units
            """ + SALE_JOINS + SALE_UNITS + SALE_FILTER + """
              AND %s
            GROUP BY 1
            ORDER BY 1
            """.formatted(VALID);

    static final String BY_BRANCH_SQL = """
            SELECT b.id AS branch_id,
                   b.name AS branch_name,
                   count(*) AS orders,
                   coalesce(sum(s.total), 0) AS revenue,
                   coalesce(sum(u.units), 0) AS units
            """ + SALE_JOINS + SALE_UNITS + SALE_FILTER + """
              AND %s
            GROUP BY b.id, b.name
            ORDER BY revenue DESC, b.name
            """.formatted(VALID);

    static final String BY_CATEGORY_SQL = """
            SELECT coalesce(d.category_id_snapshot, p.category_id) AS category_id,
                   c.code AS category_code,
                   coalesce(sum(%s), 0) AS revenue,
                   coalesce(sum(d.units), 0) AS units
            """.formatted(LINE_REVENUE) + LINE_JOINS + """
            LEFT JOIN categories c ON c.id = coalesce(d.category_id_snapshot, p.category_id)
            """ + SALE_FILTER + """
              AND %s
            GROUP BY 1, 2
            ORDER BY revenue DESC
            """.formatted(VALID);

    static final String TOP_PRODUCTS_SQL = """
            SELECT d.product_id,
                   coalesce(max(p.name), max(d.product_name_snapshot)) AS name,
                   coalesce(max(p.sku), max(d.sku_snapshot)) AS sku,
                   coalesce(sum(%s), 0) AS revenue,
                   coalesce(sum(d.units), 0) AS units
            """.formatted(LINE_REVENUE) + LINE_JOINS + SALE_FILTER + """
              AND %s
            GROUP BY d.product_id
            ORDER BY revenue DESC, units DESC
            LIMIT :limit
            """.formatted(VALID);

    static final String PRODUCT_DAILY_SQL = """
            SELECT d.product_id,
                   coalesce(max(p.name), max(d.product_name_snapshot)) AS name,
                   (s.created_at AT TIME ZONE 'America/Bogota')::date AS day,
                   coalesce(sum(d.units), 0) AS units,
                   coalesce(sum(%s), 0) AS revenue
            """.formatted(LINE_REVENUE) + LINE_JOINS + SALE_FILTER + """
              AND %s
            GROUP BY d.product_id, 3
            """.formatted(VALID);

    static final String WEEKLY_REVENUE_SQL = """
            SELECT date_trunc('week', s.created_at AT TIME ZONE 'America/Bogota')::date AS week_start,
                   coalesce(sum(s.total), 0) AS revenue
            """ + SALE_JOINS + """
            WHERE b.store_id = :storeId
              AND s.deleted_at IS NULL
              AND (CAST(:branchId AS uuid) IS NULL OR s.branch_id = CAST(:branchId AS uuid))
              AND s.created_at < :toTs
              AND %s
            GROUP BY 1
            ORDER BY 1
            """.formatted(VALID);

    static final String PRODUCT_STOCK_SQL = """
            SELECT p.id AS product_id,
                   p.name,
                   p.sku,
                   p.created_at,
                   coalesce(sum(i.stock), 0) AS stock,
                   max(coalesce(rp.reorder_point, %d)) AS min_stock
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            LEFT JOIN inventory_reorder_policies rp ON rp.inventory_id = i.id AND rp.valid_to IS NULL
            WHERE b.store_id = :storeId
              AND p.store_id = :storeId
              AND i.deleted_at IS NULL
              AND (CAST(:branchId AS uuid) IS NULL OR i.branch_id = CAST(:branchId AS uuid))
            GROUP BY p.id, p.name, p.sku, p.created_at
            """.formatted(DEFAULT_MIN_STOCK);

    /** Productos del almacén con estado {@code ACTIVE} (publicados), mismo criterio que el catálogo. */
    static final String ACTIVE_PRODUCTS_SQL = """
            SELECT p.id AS product_id
            FROM products p
            JOIN states ps ON ps.id = p.state_id AND ps.deleted_at IS NULL
            WHERE p.store_id = :storeId
              AND p.deleted_at IS NULL
              AND ps.code = 'ACTIVE'
            """;

    static final String BRANCH_IN_STORE_SQL = """
            SELECT EXISTS (SELECT 1 FROM branches WHERE id = :branchId AND store_id = :storeId) AS found
            """;

    private final JdbcClient jdbcClient;

    record Totals(BigDecimal revenue, long orders, long units, BigDecimal discounts, long customers,
                  long cancelledOrders) {
    }

    record DailySales(LocalDate day, BigDecimal revenue, long orders, long units) {
    }

    record ProductDailySales(UUID productId, String name, LocalDate day, long units, BigDecimal revenue) {
    }

    record WeeklyRevenue(LocalDate weekStart, BigDecimal revenue) {
    }

    record ProductStock(UUID productId, String name, String sku, OffsetDateTime createdAt, long stock,
                        int minStock) {
    }

    /** 404 {@code BRANCH_NOT_FOUND} si se indica una sucursal que no es del almacén. */
    void requireBranch(UUID storeId, UUID branchId) {
        if (branchId != null && !branchInStore(storeId, branchId)) {
            throw new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal.");
        }
    }

    boolean branchInStore(UUID storeId, UUID branchId) {
        return jdbcClient.sql(BRANCH_IN_STORE_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId)
                .query((result, row) -> result.getBoolean("found"))
                .optional()
                .orElse(false);
    }

    Totals totals(UUID storeId, UUID branchId, DateRange range) {
        return scoped(TOTALS_SQL, storeId, branchId, range)
                .query((result, row) -> new Totals(
                        Analytics.nonNull(result.getBigDecimal("revenue")),
                        result.getLong("orders"),
                        result.getLong("units"),
                        Analytics.nonNull(result.getBigDecimal("discounts")),
                        result.getLong("customers"),
                        result.getLong("cancelled_orders")))
                .optional()
                .orElse(new Totals(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, 0));
    }

    List<DailySales> daily(UUID storeId, UUID branchId, DateRange range) {
        return scoped(DAILY_SQL, storeId, branchId, range)
                .query((result, row) -> new DailySales(
                        result.getObject("day", LocalDate.class),
                        Analytics.nonNull(result.getBigDecimal("revenue")),
                        result.getLong("orders"),
                        result.getLong("units")))
                .list();
    }

    List<SalesReport.BranchSales> byBranch(UUID storeId, UUID branchId, DateRange range) {
        return scoped(BY_BRANCH_SQL, storeId, branchId, range)
                .query((result, row) -> new SalesReport.BranchSales(
                        result.getObject("branch_id", UUID.class),
                        result.getString("branch_name"),
                        Analytics.money(result.getBigDecimal("revenue")),
                        result.getLong("orders"),
                        result.getLong("units")))
                .list();
    }

    List<SalesReport.CategorySales> byCategory(UUID storeId, UUID branchId, DateRange range) {
        return scoped(BY_CATEGORY_SQL, storeId, branchId, range)
                .query((result, row) -> new SalesReport.CategorySales(
                        result.getObject("category_id", UUID.class),
                        result.getString("category_code"),
                        Analytics.money(result.getBigDecimal("revenue")),
                        result.getLong("units")))
                .list();
    }

    List<SalesReport.ProductSales> topProducts(UUID storeId, UUID branchId, DateRange range, int limit) {
        return scoped(TOP_PRODUCTS_SQL, storeId, branchId, range)
                .param("limit", limit)
                .query((result, row) -> new SalesReport.ProductSales(
                        result.getObject("product_id", UUID.class),
                        result.getString("name"),
                        result.getString("sku"),
                        Analytics.money(result.getBigDecimal("revenue")),
                        result.getLong("units")))
                .list();
    }

    List<ProductDailySales> productDaily(UUID storeId, UUID branchId, DateRange range) {
        return scoped(PRODUCT_DAILY_SQL, storeId, branchId, range)
                .query((result, row) -> new ProductDailySales(
                        result.getObject("product_id", UUID.class),
                        result.getString("name"),
                        result.getObject("day", LocalDate.class),
                        result.getLong("units"),
                        Analytics.nonNull(result.getBigDecimal("revenue"))))
                .list();
    }

    /** Ingresos por semana (lunes) anteriores a {@code beforeWeek}; solo semanas con ventas. */
    List<WeeklyRevenue> weeklyRevenue(UUID storeId, UUID branchId, LocalDate beforeWeek) {
        return jdbcClient.sql(WEEKLY_REVENUE_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId == null ? null : branchId.toString())
                .param("toTs", Analytics.startOf(beforeWeek))
                .query((result, row) -> new WeeklyRevenue(
                        result.getObject("week_start", LocalDate.class),
                        Analytics.nonNull(result.getBigDecimal("revenue"))))
                .list();
    }

    List<ProductStock> productStock(UUID storeId, UUID branchId) {
        return jdbcClient.sql(PRODUCT_STOCK_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId == null ? null : branchId.toString())
                .query((result, row) -> new ProductStock(
                        result.getObject("product_id", UUID.class),
                        result.getString("name"),
                        result.getString("sku"),
                        result.getObject("created_at", OffsetDateTime.class),
                        result.getLong("stock"),
                        result.getInt("min_stock")))
                .list();
    }

    Set<UUID> activeProductIds(UUID storeId) {
        return new HashSet<>(jdbcClient.sql(ACTIVE_PRODUCTS_SQL)
                .param("storeId", storeId)
                .query((result, row) -> result.getObject("product_id", UUID.class))
                .list());
    }

    private JdbcClient.StatementSpec scoped(String sql, UUID storeId, UUID branchId, DateRange range) {
        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("branchId", branchId == null ? null : branchId.toString())
                .param("fromTs", range.startInstant())
                .param("toTs", range.endInstant());
    }
}
