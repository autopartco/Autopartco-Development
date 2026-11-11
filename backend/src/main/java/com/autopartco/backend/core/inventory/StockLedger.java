package com.autopartco.backend.core.inventory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.events.ListingsChanged;

import lombok.AllArgsConstructor;

/**
 * Único punto que cambia {@code inventory.stock} desde el portal (ajustes, altas y ediciones de listados,
 * transferencias e importaciones). En la transacción del llamador: fija {@code autopartco.capture_managed_by_app}
 * para que los triggers puente no dupliquen el movimiento, bloquea los listados en el orden del checkout
 * ({@code branch_id, product_id}), actualiza el stock y escribe un {@code inventory_movements} por cambio con
 * delta distinto de cero. Al terminar restaura el valor anterior del ajuste para no silenciar otros triggers
 * puente de la misma transacción (historial de precios).
 *
 * <p>El checkout ({@link StockService#decrementIfAvailable}) y la devolución por vencimiento
 * ({@link StockService#returnStock}) no pasan por aquí.</p>
 */
@Service
@AllArgsConstructor
public class StockLedger {
    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_CSV = "CSV";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    public static final String REASON_INITIAL_LOAD = "INITIAL_LOAD";
    public static final String REASON_MANUAL_CORRECTION = "MANUAL_CORRECTION";
    public static final String REASON_TRANSFER_OUT = "TRANSFER_OUT";
    public static final String REASON_TRANSFER_IN = "TRANSFER_IN";
    public static final String REASON_CSV_IMPORT = "CSV_IMPORT";

    public static final String REFERENCE_TRANSFER_ITEM = "TRANSFER_ITEM";
    public static final String REFERENCE_IMPORT_JOB = "IMPORT_JOB";

    private static final String CAPTURE_SETTING = "autopartco.capture_managed_by_app";

    /** Devuelve el valor previo y fija el ajuste en {@code on} hasta el final de la transacción. */
    static final String CAPTURE_ON_SQL = """
            SELECT coalesce(current_setting('autopartco.capture_managed_by_app', true), '') AS previous,
                   set_config('autopartco.capture_managed_by_app', 'on', true) AS current
            """;

    static final String CAPTURE_RESTORE_SQL = """
            SELECT set_config('autopartco.capture_managed_by_app', :previous, true)
            """;

    static final String BIND_ACTOR_SQL = """
            SELECT set_config('autopartco.actor_account_id', :actor, true)
            """;

    static final String LOCK_SQL = """
            SELECT id, branch_id, product_id, stock
            FROM inventory
            WHERE id IN (:inventoryIds) AND deleted_at IS NULL
            ORDER BY branch_id, product_id
            FOR UPDATE
            """;

    static final String UPDATE_STOCK_SQL = """
            UPDATE inventory
            SET stock = :stock, version = version + 1, updated_at = now()
            WHERE id = :inventoryId
            """;

    /** Sin actor explícito usa el ligado a la transacción ({@link #bindActor}), como los triggers puente. */
    static final String INSERT_MOVEMENT_SQL = """
            INSERT INTO inventory_movements
                (inventory_id, movement_type, quantity, previous_stock, new_stock, reason_code, source,
                 actor_account_id, reference_type, reference_id, notes, occurred_at)
            VALUES
                (:inventoryId, :movementType, :quantity, :previousStock, :newStock, :reasonCode, :source,
                 coalesce(CAST(:actorAccountId AS uuid), public.app_actor_account_id()),
                 :referenceType, CAST(:referenceId AS uuid), :notes, clock_timestamp())
            RETURNING id, occurred_at
            """;

    private final JdbcClient jdbcClient;
    /** Publica {@link ListingsChanged}: {@code core.search} reindexa los productos con stock cambiado tras el commit. */
    private final ApplicationEventPublisher events;

    /** Nuevo stock absoluto de un listado vivo. {@code referenceType} y {@code referenceId} van juntos o ambos nulos. */
    public record StockChange(
            UUID inventoryId,
            int newStock,
            String reasonCode,
            String referenceType,
            UUID referenceId,
            String notes
    ) {
        public StockChange {
            if (inventoryId == null || reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("inventoryId and reasonCode are required");
            }
            if ((referenceType == null) != (referenceId == null)) {
                throw new IllegalArgumentException("referenceType and referenceId go together");
            }
        }

        public static StockChange of(UUID inventoryId, int newStock, String reasonCode, String notes) {
            return new StockChange(inventoryId, newStock, reasonCode, null, null, notes);
        }
    }

    /** Listado bloqueado con su stock actual. */
    public record LockedListing(UUID inventoryId, UUID branchId, UUID productId, int stock) {
    }

    /** Movimiento escrito. {@code movementType} es {@code IN} u {@code OUT}; {@code quantity} es |delta|. */
    public record MovementRow(
            UUID id,
            UUID inventoryId,
            UUID branchId,
            UUID productId,
            String movementType,
            int quantity,
            int previousStock,
            int newStock,
            String reasonCode,
            OffsetDateTime occurredAt
    ) {
    }

    /**
     * Bloquea ({@code FOR UPDATE}) los listados vivos en orden {@code branch_id, product_id} y devuelve su stock
     * actual en ese orden. Si falta alguno, {@code LISTING_NOT_FOUND}. Sirve para validar cantidades antes de
     * {@link #apply} en la misma transacción.
     */
    @Transactional
    public List<LockedListing> lock(Collection<UUID> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = inventoryIds.stream().distinct().toList();
        List<LockedListing> locked = jdbcClient.sql(LOCK_SQL)
                .param("inventoryIds", ids)
                .query((result, row) -> new LockedListing(
                        result.getObject("id", UUID.class),
                        result.getObject("branch_id", UUID.class),
                        result.getObject("product_id", UUID.class),
                        result.getInt("stock")))
                .list();
        if (locked.size() != ids.size()) {
            throw new NotFoundException("LISTING_NOT_FOUND", "El producto no está listado en esa sucursal.");
        }
        return locked;
    }

    /**
     * Aplica los cambios en una transacción (la del llamador): bloquea, actualiza y escribe un movimiento por cambio
     * con delta distinto de cero (los demás no escriben nada). {@code newStock < 0}: 409 {@code INSUFFICIENT_STOCK}
     * sin cambiar nada. Un listado no puede repetirse en {@code changes}.
     *
     * @param actorAccountId cuenta que origina el cambio; {@code null} usa la ligada con {@link #bindActor}
     * @param source         {@code USER}, {@code CSV}, {@code SYSTEM}… (CHECK de {@code inventory_movements.source})
     * @return movimientos escritos, en orden de bloqueo
     */
    @Transactional
    public List<MovementRow> apply(UUID actorAccountId, String source, List<StockChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        Map<UUID, StockChange> byInventory = new HashMap<>();
        List<Map<String, Object>> negative = new ArrayList<>();
        for (StockChange change : changes) {
            if (byInventory.put(change.inventoryId(), change) != null) {
                throw new IllegalArgumentException("duplicate inventoryId " + change.inventoryId());
            }
            if (change.newStock() < 0) {
                negative.add(Map.of("inventoryId", change.inventoryId(), "newStock", change.newStock()));
            }
        }
        if (!negative.isEmpty()) {
            throw insufficientStock(negative);
        }

        String previousCapture = captureOn();
        List<LockedListing> locked = lock(byInventory.keySet());
        List<MovementRow> written = new ArrayList<>();
        for (LockedListing listing : locked) {
            StockChange change = byInventory.get(listing.inventoryId());
            int delta = change.newStock() - listing.stock();
            if (delta == 0) {
                continue;
            }
            jdbcClient.sql(UPDATE_STOCK_SQL)
                    .param("stock", change.newStock())
                    .param("inventoryId", listing.inventoryId())
                    .update();
            String movementType = delta > 0 ? "IN" : "OUT";
            int quantity = Math.abs(delta);
            MovementRow row = jdbcClient.sql(INSERT_MOVEMENT_SQL)
                    .param("inventoryId", listing.inventoryId())
                    .param("movementType", movementType)
                    .param("quantity", quantity)
                    .param("previousStock", listing.stock())
                    .param("newStock", change.newStock())
                    .param("reasonCode", change.reasonCode())
                    .param("source", source)
                    .param("actorAccountId", actorAccountId == null ? null : actorAccountId.toString())
                    .param("referenceType", change.referenceType())
                    .param("referenceId", change.referenceId() == null ? null : change.referenceId().toString())
                    .param("notes", change.notes())
                    .query((result, rowNumber) -> new MovementRow(
                            result.getObject("id", UUID.class),
                            listing.inventoryId(),
                            listing.branchId(),
                            listing.productId(),
                            movementType,
                            quantity,
                            listing.stock(),
                            change.newStock(),
                            change.reasonCode(),
                            result.getObject("occurred_at", OffsetDateTime.class)))
                    .list()
                    .getFirst();
            written.add(row);
        }
        restoreCapture(previousCapture);
        if (!written.isEmpty()) {
            events.publishEvent(ListingsChanged.ofProducts(written.stream().map(MovementRow::productId).toList()));
        }
        return written;
    }

    /**
     * Liga la cuenta que actúa a la transacción ({@code autopartco.actor_account_id}): la usan los movimientos sin
     * actor explícito y los triggers puente (historial de precios).
     */
    @Transactional
    public void bindActor(UUID actorAccountId) {
        if (actorAccountId == null) {
            return;
        }
        jdbcClient.sql(BIND_ACTOR_SQL)
                .param("actor", actorAccountId.toString())
                .query((result, row) -> result.getString(1))
                .list();
    }

    private String captureOn() {
        List<String> previous = jdbcClient.sql(CAPTURE_ON_SQL)
                .query((result, row) -> result.getString("previous"))
                .list();
        return previous.isEmpty() || previous.getFirst() == null ? "" : previous.getFirst();
    }

    private void restoreCapture(String previous) {
        if ("on".equals(previous)) {
            return;
        }
        jdbcClient.sql(CAPTURE_RESTORE_SQL)
                .param("previous", previous)
                .query((result, row) -> result.getString(1))
                .list();
    }

    public static ConflictException insufficientStock(Object details) {
        return new ConflictException("INSUFFICIENT_STOCK", "No hay existencias suficientes.", details);
    }
}
