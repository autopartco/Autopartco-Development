package com.autopartco.backend.core.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.inventory.StockLedger.LockedListing;
import com.autopartco.backend.core.inventory.StockLedger.MovementRow;
import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

class StockLedgerTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID BRANCH_1 = UUID.randomUUID();
    private static final UUID BRANCH_2 = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    private FakeJdbc jdbc;
    private StockLedger ledger;

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbc();
        ledger = new StockLedger(jdbc.client, event -> { });
    }

    private static Map<String, Object> listing(UUID id, UUID branchId, int stock) {
        return Map.of("id", id, "branch_id", branchId, "product_id", PRODUCT, "stock", stock);
    }

    private static Map<String, Object> inserted() {
        return Map.of("id", UUID.randomUUID(), "occurred_at", NOW);
    }

    private static Map<String, Object> previousCapture(String value) {
        Map<String, Object> row = new HashMap<>();
        row.put("previous", value);
        return row;
    }

    @Test
    void appliesChangesInLockOrderAndWritesOneMovementPerChange() {
        // La BD devuelve el orden de bloqueo (branch_id, product_id), no el de entrada.
        jdbc.returns(StockLedger.CAPTURE_ON_SQL, previousCapture(""))
                .returns(StockLedger.LOCK_SQL, listing(B, BRANCH_1, 5), listing(A, BRANCH_2, 2))
                .returns(StockLedger.INSERT_MOVEMENT_SQL, inserted())
                .returns(StockLedger.INSERT_MOVEMENT_SQL, inserted());

        List<MovementRow> rows = ledger.apply(ACTOR, StockLedger.SOURCE_USER, List.of(
                StockChange.of(A, 4, "COUNT_ADJUSTMENT", "conteo"),
                StockChange.of(B, 1, "DAMAGE", null)));

        assertThat(jdbc.sqlSequence()).containsExactly(
                StockLedger.CAPTURE_ON_SQL,
                StockLedger.LOCK_SQL,
                StockLedger.UPDATE_STOCK_SQL, StockLedger.INSERT_MOVEMENT_SQL,
                StockLedger.UPDATE_STOCK_SQL, StockLedger.INSERT_MOVEMENT_SQL,
                StockLedger.CAPTURE_RESTORE_SQL);
        assertThat(StockLedger.LOCK_SQL).contains("ORDER BY branch_id, product_id").contains("FOR UPDATE")
                .contains("deleted_at IS NULL");
        assertThat(StockLedger.CAPTURE_ON_SQL)
                .contains("set_config('autopartco.capture_managed_by_app', 'on', true)");

        assertThat(rows).extracting(MovementRow::inventoryId).containsExactly(B, A);
        assertThat(rows).extracting(MovementRow::movementType).containsExactly("OUT", "IN");
        assertThat(rows).extracting(MovementRow::quantity).containsExactly(4, 2);

        Map<String, Object> outMovement = jdbc.callsTo(StockLedger.INSERT_MOVEMENT_SQL).get(0).params();
        assertThat(outMovement).containsEntry("inventoryId", B).containsEntry("movementType", "OUT")
                .containsEntry("quantity", 4).containsEntry("previousStock", 5).containsEntry("newStock", 1)
                .containsEntry("reasonCode", "DAMAGE").containsEntry("source", "USER")
                .containsEntry("actorAccountId", ACTOR.toString()).containsEntry("referenceType", null)
                .containsEntry("referenceId", null);
        Map<String, Object> inMovement = jdbc.callsTo(StockLedger.INSERT_MOVEMENT_SQL).get(1).params();
        assertThat(inMovement).containsEntry("inventoryId", A).containsEntry("movementType", "IN")
                .containsEntry("previousStock", 2).containsEntry("newStock", 4).containsEntry("notes", "conteo");
        assertThat(jdbc.callsTo(StockLedger.UPDATE_STOCK_SQL).get(0).params())
                .containsEntry("stock", 1).containsEntry("inventoryId", B);
        assertThat(jdbc.callsTo(StockLedger.CAPTURE_RESTORE_SQL).get(0).params()).containsEntry("previous", "");
    }

    @Test
    void zeroDeltaWritesNothing() {
        jdbc.returns(StockLedger.CAPTURE_ON_SQL, previousCapture(""))
                .returns(StockLedger.LOCK_SQL, listing(A, BRANCH_1, 3));

        List<MovementRow> rows = ledger.apply(ACTOR, StockLedger.SOURCE_USER,
                List.of(StockChange.of(A, 3, StockLedger.REASON_MANUAL_CORRECTION, null)));

        assertThat(rows).isEmpty();
        assertThat(jdbc.callsTo(StockLedger.UPDATE_STOCK_SQL)).isEmpty();
        assertThat(jdbc.callsTo(StockLedger.INSERT_MOVEMENT_SQL)).isEmpty();
    }

    @Test
    void negativeStockIsInsufficientStockWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> ledger.apply(ACTOR, StockLedger.SOURCE_USER, List.of(
                StockChange.of(A, 2, "COUNT_ADJUSTMENT", null),
                StockChange.of(B, -1, "DAMAGE", null))))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INSUFFICIENT_STOCK"));

        verify(jdbc.client, never()).sql(anyString());
    }

    @Test
    void missingListingIsNotFoundBeforeAnyUpdate() {
        jdbc.returns(StockLedger.CAPTURE_ON_SQL, previousCapture(""))
                .returns(StockLedger.LOCK_SQL, listing(A, BRANCH_1, 3));

        assertThatThrownBy(() -> ledger.apply(ACTOR, StockLedger.SOURCE_USER, List.of(
                StockChange.of(A, 4, "COUNT_ADJUSTMENT", null),
                StockChange.of(B, 4, "COUNT_ADJUSTMENT", null))))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_FOUND"));

        assertThat(jdbc.callsTo(StockLedger.UPDATE_STOCK_SQL)).isEmpty();
    }

    @Test
    void repeatedListingIsRejected() {
        assertThatThrownBy(() -> ledger.apply(ACTOR, StockLedger.SOURCE_USER, List.of(
                StockChange.of(A, 4, "COUNT_ADJUSTMENT", null),
                StockChange.of(A, 5, "COUNT_ADJUSTMENT", null))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbc.client, never()).sql(anyString());
    }

    @Test
    void emptyChangesDoNothing() {
        assertThat(ledger.apply(ACTOR, StockLedger.SOURCE_USER, List.of())).isEmpty();
        verify(jdbc.client, never()).sql(anyString());
    }

    @Test
    void referenceIsWrittenAndCaptureAlreadyOnIsNotRestored() {
        UUID transferItem = UUID.randomUUID();
        jdbc.returns(StockLedger.CAPTURE_ON_SQL, previousCapture("on"))
                .returns(StockLedger.LOCK_SQL, listing(A, BRANCH_1, 6))
                .returns(StockLedger.INSERT_MOVEMENT_SQL, inserted());

        ledger.apply(null, StockLedger.SOURCE_USER, List.of(new StockChange(A, 2, StockLedger.REASON_TRANSFER_OUT,
                StockLedger.REFERENCE_TRANSFER_ITEM, transferItem, null)));

        assertThat(jdbc.callsTo(StockLedger.INSERT_MOVEMENT_SQL).getFirst().params())
                .containsEntry("referenceType", "TRANSFER_ITEM")
                .containsEntry("referenceId", transferItem.toString())
                .containsEntry("actorAccountId", null);
        assertThat(StockLedger.INSERT_MOVEMENT_SQL).contains("public.app_actor_account_id()");
        assertThat(jdbc.callsTo(StockLedger.CAPTURE_RESTORE_SQL)).isEmpty();
    }

    @Test
    void referenceTypeAndIdGoTogether() {
        assertThatThrownBy(() -> new StockChange(A, 1, "TRANSFER_IN", "TRANSFER_ITEM", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockReturnsCurrentStockInLockOrder() {
        jdbc.returns(StockLedger.LOCK_SQL, listing(B, BRANCH_1, 5), listing(A, BRANCH_2, 2));

        List<LockedListing> locked = ledger.lock(List.of(A, B, A));

        assertThat(locked).extracting(LockedListing::inventoryId).containsExactly(B, A);
        assertThat(locked).extracting(LockedListing::stock).containsExactly(5, 2);
        assertThat(jdbc.callsTo(StockLedger.LOCK_SQL).getFirst().params().get("inventoryIds"))
                .isEqualTo(List.of(A, B));
    }

    @Test
    void bindActorSetsTransactionSetting() {
        ledger.bindActor(ACTOR);

        assertThat(jdbc.sqlSequence()).containsExactly(StockLedger.BIND_ACTOR_SQL);
        assertThat(StockLedger.BIND_ACTOR_SQL).contains("autopartco.actor_account_id").contains("true");
        assertThat(jdbc.callsTo(StockLedger.BIND_ACTOR_SQL).getFirst().params())
                .containsEntry("actor", ACTOR.toString());
    }
}
