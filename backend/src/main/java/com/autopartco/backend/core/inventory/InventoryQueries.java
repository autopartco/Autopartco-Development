package com.autopartco.backend.core.inventory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Read model del inventario de un almacén (§4.2.5): listados, historial de movimientos, catálogo de motivos y stock
 * bajo. Cruza {@code inventory}, {@code inventory_movements}, {@code inventory_movement_reasons},
 * {@code inventory_reorder_policies}, {@code branches}, {@code products}, {@code accounts} y {@code persons}. Todas
 * las consultas filtran por el almacén; no comprueba permisos (lo hace el caso de uso).
 */
@Service
@AllArgsConstructor
public class InventoryQueries {
    /** Umbral de stock bajo sin política vigente. */
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 3;
    /** Tope de filas del listado de stock bajo (no paginado). */
    public static final int LOW_STOCK_LIMIT = 1000;

    static final String LISTING_SQL = """
            SELECT i.id, i.stock
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            WHERE b.store_id = :storeId AND i.branch_id = :branchId AND i.product_id = :productId
              AND p.store_id = :storeId AND i.deleted_at IS NULL
            """;

    static final String REASONS_SQL = """
            SELECT code, description, direction
            FROM inventory_movement_reasons
            WHERE code IN (:codes) AND active
            ORDER BY description
            """;

    private static final String MOVEMENTS_FROM = """
            FROM inventory_movements m
            JOIN inventory i ON i.id = m.inventory_id
            JOIN branches b ON b.id = i.branch_id
            JOIN products p ON p.id = i.product_id
            JOIN inventory_movement_reasons r ON r.code = m.reason_code
            LEFT JOIN accounts a ON a.id = m.actor_account_id
            LEFT JOIN persons pe ON pe.id = a.id_person
            WHERE b.store_id = :storeId AND m.deleted_at IS NULL
            """;

    private static final String MOVEMENT_COLUMNS = """
            SELECT m.id, m.occurred_at, i.branch_id, b.name AS branch_name, i.product_id, p.name AS product_name,
                   p.sku, m.movement_type, m.quantity, m.previous_stock, m.new_stock, m.reason_code,
                   r.description AS reason_label,
                   nullif(trim(concat_ws(' ', pe.name, pe.surname)), '') AS actor_name,
                   m.reference_type, m.reference_id, m.notes
            """;

    static final String LOW_STOCK_SQL = """
            SELECT i.product_id, p.name AS product_name, p.sku, i.branch_id, b.name AS branch_name, i.stock,
                   coalesce(rp.reorder_point, 3) AS min_stock
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            LEFT JOIN inventory_reorder_policies rp ON rp.inventory_id = i.id AND rp.valid_to IS NULL
            WHERE b.store_id = :storeId AND i.deleted_at IS NULL
              AND (CAST(:branchId AS uuid) IS NULL OR i.branch_id = CAST(:branchId AS uuid))
              AND i.stock <= coalesce(rp.reorder_point, 3)
            ORDER BY i.stock, p.name, b.name
            LIMIT :limit
            """;

    /** Tope de filas de la exportación CSV. */
    public static final int EXPORT_LIMIT = 50_000;

    static final String BRANCH_PRICES_SQL = """
            SELECT i.product_id, i.price
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            WHERE b.store_id = :storeId AND i.branch_id = :branchId AND p.store_id = :storeId
              AND i.deleted_at IS NULL AND i.product_id IN (:productIds)
            """;

    static final String EXPORT_SQL = """
            SELECT p.sku, p.part_number, p.name AS product_name, b.name AS branch_name, i.stock, i.price,
                   rp.reorder_point, ps.code AS product_state
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            JOIN states ps ON ps.id = p.state_id
            LEFT JOIN inventory_reorder_policies rp ON rp.inventory_id = i.id AND rp.valid_to IS NULL
            WHERE b.store_id = :storeId AND p.store_id = :storeId AND i.deleted_at IS NULL
              AND (CAST(:branchId AS uuid) IS NULL OR i.branch_id = CAST(:branchId AS uuid))
            ORDER BY p.name, b.name, i.id
            LIMIT :limit
            """;

    static final String IMPORT_LOOKUP_SQL = """
            SELECT i.id AS inventory_id, i.product_id, p.sku, p.part_number, b.name AS branch_name, i.stock, i.price,
                   rp.reorder_point
            FROM inventory i
            JOIN branches b ON b.id = i.branch_id AND b.deleted_at IS NULL
            JOIN products p ON p.id = i.product_id AND p.deleted_at IS NULL
            LEFT JOIN inventory_reorder_policies rp ON rp.inventory_id = i.id AND rp.valid_to IS NULL
            WHERE b.store_id = :storeId AND p.store_id = :storeId AND i.deleted_at IS NULL
              AND (lower(p.sku) IN (:keys) OR lower(p.part_number) IN (:keys))
            """;

    private final JdbcClient jdbcClient;

    /** Fila de la exportación de inventario. {@code minStock} nulo = sin política vigente. */
    public record ExportRow(
            String sku,
            String partNumber,
            String productName,
            String branchName,
            int stock,
            java.math.BigDecimal price,
            Integer minStock,
            String productState
    ) {
    }

    /** Listado candidato de una importación con sus valores actuales. */
    public record ImportCandidate(
            UUID inventoryId,
            UUID productId,
            String sku,
            String partNumber,
            String branchName,
            int stock,
            java.math.BigDecimal price,
            Integer minStock
    ) {
    }

    /** Listado vivo de la sucursal y producto del almacén. */
    public record ListingRef(UUID inventoryId, int stock) {
    }

    public record ReasonView(String code, String label, String direction) {
    }

    public record MovementView(
            UUID id,
            OffsetDateTime occurredAt,
            UUID branchId,
            String branchName,
            UUID productId,
            String productName,
            String sku,
            String movementType,
            int quantity,
            int previousStock,
            int newStock,
            String reasonCode,
            String reasonLabel,
            String actorName,
            String referenceType,
            UUID referenceId,
            String notes
    ) {
    }

    /** Filtros opcionales del historial; {@code from} incluido y {@code to} excluido. */
    public record MovementFilter(
            UUID branchId,
            UUID productId,
            String reasonCode,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
    }

    public record LowStockView(
            UUID productId,
            String productName,
            String sku,
            UUID branchId,
            String branchName,
            int stock,
            int minStock
    ) {
    }

    @Transactional(readOnly = true)
    public Optional<ListingRef> listing(UUID storeId, UUID branchId, UUID productId) {
        return jdbcClient.sql(LISTING_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId)
                .param("productId", productId)
                .query((result, row) -> new ListingRef(result.getObject("id", UUID.class), result.getInt("stock")))
                .optional();
    }

    /**
     * Precio actual de los productos del almacén listados en la sucursal. Los que no son del almacén, no están
     * listados ahí o fueron borrados no aparecen en el mapa.
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, java.math.BigDecimal> prices(UUID storeId, UUID branchId, Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<UUID, java.math.BigDecimal> prices = new java.util.HashMap<>();
        jdbcClient.sql(BRANCH_PRICES_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId)
                .param("productIds", List.copyOf(productIds))
                .query((result, row) -> {
                    prices.put(result.getObject("product_id", UUID.class), result.getBigDecimal("price"));
                    return 0;
                })
                .list();
        return prices;
    }

    /** Inventario del almacén (o de una sucursal) para exportar, hasta {@value #EXPORT_LIMIT} filas. */
    @Transactional(readOnly = true)
    public List<ExportRow> exportRows(UUID storeId, UUID branchId) {
        return jdbcClient.sql(EXPORT_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId == null ? null : branchId.toString())
                .param("limit", EXPORT_LIMIT)
                .query((result, row) -> new ExportRow(
                        result.getString("sku"),
                        result.getString("part_number"),
                        result.getString("product_name"),
                        result.getString("branch_name"),
                        result.getInt("stock"),
                        result.getBigDecimal("price"),
                        result.getObject("reorder_point", Integer.class),
                        result.getString("product_state")))
                .list();
    }

    /**
     * Listados vivos del almacén cuyo SKU o referencia coincide (sin distinguir mayúsculas) con alguna de
     * {@code keys} (en minúsculas). El llamador resuelve la coincidencia exacta y las ambigüedades.
     */
    @Transactional(readOnly = true)
    public List<ImportCandidate> importCandidates(UUID storeId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(IMPORT_LOOKUP_SQL)
                .param("storeId", storeId)
                .param("keys", List.copyOf(keys))
                .query((result, row) -> new ImportCandidate(
                        result.getObject("inventory_id", UUID.class),
                        result.getObject("product_id", UUID.class),
                        result.getString("sku"),
                        result.getString("part_number"),
                        result.getString("branch_name"),
                        result.getInt("stock"),
                        result.getBigDecimal("price"),
                        result.getObject("reorder_point", Integer.class)))
                .list();
    }

    /** Motivos activos entre {@code codes}, ordenados por descripción. */
    @Transactional(readOnly = true)
    public List<ReasonView> reasons(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(REASONS_SQL)
                .param("codes", List.copyOf(codes))
                .query((result, row) -> new ReasonView(
                        result.getString("code"),
                        result.getString("description"),
                        result.getString("direction")))
                .list();
    }

    /** Historial del almacén, del más reciente al más antiguo. {@code page} desde 1. */
    @Transactional(readOnly = true)
    public PageResponse<MovementView> movements(UUID storeId, MovementFilter filter, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object[]> params = new ArrayList<>();
        params.add(new Object[] {"storeId", storeId});
        if (filter.branchId() != null) {
            where.append(" AND i.branch_id = :branchId");
            params.add(new Object[] {"branchId", filter.branchId()});
        }
        if (filter.productId() != null) {
            where.append(" AND i.product_id = :productId");
            params.add(new Object[] {"productId", filter.productId()});
        }
        if (filter.reasonCode() != null) {
            where.append(" AND m.reason_code = :reasonCode");
            params.add(new Object[] {"reasonCode", filter.reasonCode()});
        }
        if (filter.from() != null) {
            where.append(" AND m.occurred_at >= :from");
            params.add(new Object[] {"from", filter.from()});
        }
        if (filter.to() != null) {
            where.append(" AND m.occurred_at < :to");
            params.add(new Object[] {"to", filter.to()});
        }

        JdbcClient.StatementSpec count = jdbcClient.sql("SELECT count(*) " + MOVEMENTS_FROM + where);
        for (Object[] param : params) {
            count = count.param((String) param[0], param[1]);
        }
        long total = count.query(Long.class).single();
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        JdbcClient.StatementSpec select = jdbcClient.sql(MOVEMENT_COLUMNS + MOVEMENTS_FROM + where
                + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limit OFFSET :offset");
        for (Object[] param : params) {
            select = select.param((String) param[0], param[1]);
        }
        List<MovementView> items = select
                .param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query((result, row) -> toMovement(result))
                .list();
        return PageResponse.of(items, page, size, total);
    }

    /** Un movimiento del almacén. */
    @Transactional(readOnly = true)
    public Optional<MovementView> movement(UUID storeId, UUID movementId) {
        return jdbcClient.sql(MOVEMENT_COLUMNS + MOVEMENTS_FROM + " AND m.id = :movementId")
                .param("storeId", storeId)
                .param("movementId", movementId)
                .query((result, row) -> toMovement(result))
                .optional();
    }

    /**
     * Listados con {@code stock ≤} punto de reorden vigente (o {@value #DEFAULT_LOW_STOCK_THRESHOLD}), de menor a mayor
     * stock; como mucho {@value #LOW_STOCK_LIMIT}.
     */
    @Transactional(readOnly = true)
    public List<LowStockView> lowStock(UUID storeId, UUID branchId) {
        return jdbcClient.sql(LOW_STOCK_SQL)
                .param("storeId", storeId)
                .param("branchId", branchId == null ? null : branchId.toString())
                .param("limit", LOW_STOCK_LIMIT)
                .query((result, row) -> new LowStockView(
                        result.getObject("product_id", UUID.class),
                        result.getString("product_name"),
                        result.getString("sku"),
                        result.getObject("branch_id", UUID.class),
                        result.getString("branch_name"),
                        result.getInt("stock"),
                        result.getInt("min_stock")))
                .list();
    }

    private static MovementView toMovement(java.sql.ResultSet result) throws java.sql.SQLException {
        return new MovementView(
                result.getObject("id", UUID.class),
                result.getObject("occurred_at", OffsetDateTime.class),
                result.getObject("branch_id", UUID.class),
                result.getString("branch_name"),
                result.getObject("product_id", UUID.class),
                result.getString("product_name"),
                result.getString("sku"),
                result.getString("movement_type"),
                result.getInt("quantity"),
                result.getInt("previous_stock"),
                result.getInt("new_stock"),
                result.getString("reason_code"),
                result.getString("reason_label"),
                result.getString("actor_name"),
                result.getString("reference_type"),
                result.getObject("reference_id", UUID.class),
                result.getString("notes"));
    }
}
