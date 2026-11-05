package com.autopartco.backend.core.inventory;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Políticas de reorden por listado ({@code inventory_reorder_policies}, con historial de vigencia). El portal fija el
 * stock mínimo como política {@code MANUAL} con {@code safety_stock = 0}: cierra la vigente e inserta la nueva en la
 * misma transacción. No comprueba que el listado sea del almacén: lo hace el llamador (y el trigger de consistencia).
 */
@Service
@AllArgsConstructor
public class ReorderPolicies {
    /** Serializa cambios concurrentes de la política del mismo listado (índice único parcial de la vigente). */
    static final String LOCK_LISTING_SQL = """
            SELECT id FROM inventory WHERE id = :inventoryId FOR UPDATE
            """;

    /** {@code valid_to > valid_from} aunque la vigente se haya creado en esta misma transacción. */
    static final String CLOSE_SQL = """
            UPDATE inventory_reorder_policies
            SET valid_to = greatest(clock_timestamp(), valid_from + interval '1 microsecond')
            WHERE inventory_id = :inventoryId AND valid_to IS NULL
            """;

    static final String INSERT_SQL = """
            INSERT INTO inventory_reorder_policies
                (inventory_id, store_id, reorder_point, safety_stock, source, valid_from, created_by_account_id)
            VALUES
                (:inventoryId, :storeId, :reorderPoint, 0, 'MANUAL', clock_timestamp(), CAST(:actorAccountId AS uuid))
            """;

    private final JdbcClient jdbcClient;

    /**
     * Fija el stock mínimo (punto de reorden) del listado. {@code null} solo cierra la política vigente (vuelve al
     * umbral por defecto).
     */
    @Transactional
    public void setMinStock(UUID storeId, UUID inventoryId, Integer minStock, UUID actorAccountId) {
        if (minStock != null && minStock < 0) {
            throw new IllegalArgumentException("minStock must be >= 0");
        }
        jdbcClient.sql(LOCK_LISTING_SQL)
                .param("inventoryId", inventoryId)
                .query((result, row) -> result.getObject("id", UUID.class))
                .list();
        jdbcClient.sql(CLOSE_SQL)
                .param("inventoryId", inventoryId)
                .update();
        if (minStock == null) {
            return;
        }
        jdbcClient.sql(INSERT_SQL)
                .param("inventoryId", inventoryId)
                .param("storeId", storeId)
                .param("reorderPoint", minStock)
                .param("actorAccountId", actorAccountId == null ? null : actorAccountId.toString())
                .update();
    }
}
