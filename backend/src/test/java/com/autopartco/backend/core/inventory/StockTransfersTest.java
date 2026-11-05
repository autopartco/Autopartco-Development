package com.autopartco.backend.core.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.core.inventory.StockTransfers.TransferCommand;
import com.autopartco.backend.core.inventory.StockTransfers.TransferLine;
import com.autopartco.backend.core.inventory.StockTransfers.TransferResult;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

class StockTransfersTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T10:00:00Z");
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID FROM = UUID.randomUUID();
    private static final UUID TO = UUID.randomUUID();
    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();
    private static final UUID S1 = UUID.randomUUID();
    private static final UUID S2 = UUID.randomUUID();
    private static final UUID D1 = UUID.randomUUID();
    private static final UUID D2 = UUID.randomUUID();

    private FakeJdbc jdbc;
    private StockLedger ledger;
    private StockTransfers transfers;

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbc();
        ledger = mock(StockLedger.class);
        transfers = new StockTransfers(jdbc.client, ledger);
    }

    private static Map<String, Object> source(UUID id, UUID productId, String price) {
        return Map.of("id", id, "product_id", productId, "price", new BigDecimal(price), "product_name", "Producto");
    }

    private static Map<String, Object> destination(UUID id, UUID productId) {
        return Map.of("id", id, "product_id", productId);
    }

    private static Map<String, Object> locked(UUID id, UUID branchId, UUID productId, int stock, boolean deleted) {
        return Map.of("id", id, "branch_id", branchId, "product_id", productId, "stock", stock, "deleted", deleted);
    }

    private static Map<String, Object> header(UUID id, String mode, int units, int count) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("from_branch_id", FROM);
        row.put("from_branch_name", "Centro");
        row.put("to_branch_id", TO);
        row.put("to_branch_name", "Norte");
        row.put("mode", mode);
        row.put("total_units", units);
        row.put("item_count", count);
        row.put("notes", null);
        row.put("actor_name", "Ana Pérez");
        row.put("created_at", NOW);
        return row;
    }

    private static TransferCommand items(String key, TransferLine... lines) {
        return new TransferCommand(STORE, FROM, TO, StockTransfers.MODE_ITEMS, List.of(lines), "reposición", key);
    }

    private static TransferCommand all() {
        return new TransferCommand(STORE, FROM, TO, StockTransfers.MODE_ALL, null, null, null);
    }

    /** Programa la relectura final: el id de cabecera lo genera el servicio, así que se acepta cualquiera. */
    private void readBack(String mode, int units, int count) {
        jdbc.returns(StockTransfers.HEADER_SQL, header(UUID.randomUUID(), mode, units, count));
    }

    @SuppressWarnings("unchecked")
    private List<StockChange> appliedChanges() {
        ArgumentCaptor<List<StockChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledger).apply(eq(ACTOR), eq(StockLedger.SOURCE_USER), captor.capture());
        return captor.getValue();
    }

    private void assertNothingWritten() {
        assertThat(jdbc.callsTo(StockTransfers.INSERT_LISTING_SQL)).isEmpty();
        assertThat(jdbc.callsTo(StockTransfers.REACTIVATE_LISTING_SQL)).isEmpty();
        assertThat(jdbc.callsTo(StockTransfers.INSERT_TRANSFER_SQL)).isEmpty();
        assertThat(jdbc.callsTo(StockTransfers.INSERT_ITEM_SQL)).isEmpty();
        verify(ledger, never()).apply(any(), anyString(), anyList());
    }

    @Test
    void allMovesEveryListingWithStockAndCreatesMissingDestination() {
        jdbc.returns(StockTransfers.SOURCES_WITH_STOCK_SQL, source(S1, P1, "10000.00"), source(S2, P2, "2500.50"))
                .returns(StockTransfers.DESTINATIONS_SQL, destination(D1, P1))
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 5, false), locked(S2, FROM, P2, 2, false),
                        locked(D1, TO, P1, 1, false))
                .returns(StockTransfers.INSERT_LISTING_SQL, Map.of("id", D2));
        readBack("ALL", 7, 2);

        TransferResult result = transfers.transfer(ACTOR, all());

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().fromBranchName()).isEqualTo("Centro");
        assertThat(jdbc.callsTo(StockTransfers.SOURCES_WITH_STOCK_SQL).getFirst().params())
                .containsEntry("storeId", STORE).containsEntry("branchId", FROM);
        assertThat(StockTransfers.SOURCES_WITH_STOCK_SQL).contains("i.stock > 0").contains("p.store_id = :storeId");
        assertThat(StockTransfers.LOCK_SQL).contains("ORDER BY branch_id, product_id").contains("FOR UPDATE");
        assertThat(jdbc.callsTo(StockTransfers.LOCK_SQL).getFirst().params().get("inventoryIds"))
                .isEqualTo(List.of(S1, S2, D1));
        assertThat(jdbc.callsTo(StockTransfers.IDEMPOTENCY_LOCK_SQL)).isEmpty();

        // Destino sin listado: se crea con el precio del origen y stock 0.
        assertThat(jdbc.callsTo(StockTransfers.INSERT_LISTING_SQL)).singleElement()
                .satisfies(call -> assertThat(call.params()).containsEntry("branchId", TO)
                        .containsEntry("productId", P2).containsEntry("price", new BigDecimal("2500.50")));
        assertThat(jdbc.callsTo(StockTransfers.REACTIVATE_LISTING_SQL)).isEmpty();

        Map<String, Object> headerParams = jdbc.callsTo(StockTransfers.INSERT_TRANSFER_SQL).getFirst().params();
        assertThat(headerParams).containsEntry("storeId", STORE).containsEntry("mode", "ALL")
                .containsEntry("totalUnits", 7).containsEntry("itemCount", 2)
                .containsEntry("actorAccountId", ACTOR.toString()).containsEntry("idempotencyKey", null);
        UUID transferId = (UUID) headerParams.get("id");

        List<FakeJdbc.Call> items = jdbc.callsTo(StockTransfers.INSERT_ITEM_SQL);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).params()).containsEntry("transferId", transferId).containsEntry("productId", P1)
                .containsEntry("fromInventoryId", S1).containsEntry("toInventoryId", D1).containsEntry("quantity", 5);
        assertThat(items.get(1).params()).containsEntry("productId", P2).containsEntry("fromInventoryId", S2)
                .containsEntry("toInventoryId", D2).containsEntry("quantity", 2);

        List<StockChange> changes = appliedChanges();
        assertThat(changes).extracting(StockChange::inventoryId).containsExactly(S1, D1, S2, D2);
        assertThat(changes).extracting(StockChange::newStock).containsExactly(0, 6, 0, 2);
        assertThat(changes).extracting(StockChange::reasonCode).containsExactly(
                "TRANSFER_OUT", "TRANSFER_IN", "TRANSFER_OUT", "TRANSFER_IN");
        assertThat(changes).extracting(StockChange::referenceType).containsOnly("TRANSFER_ITEM");
        assertThat(changes.get(0).referenceId()).isEqualTo(items.get(0).params().get("id"))
                .isEqualTo(changes.get(1).referenceId());
        assertThat(changes.get(2).referenceId()).isEqualTo(items.get(1).params().get("id"));

        // Orden: bloquear y validar antes de escribir; el ledger al final y luego la relectura.
        assertThat(jdbc.sqlSequence()).containsSubsequence(StockTransfers.LOCK_SQL,
                StockTransfers.INSERT_LISTING_SQL, StockTransfers.INSERT_TRANSFER_SQL,
                StockTransfers.INSERT_ITEM_SQL, StockTransfers.HEADER_SQL, StockTransfers.ITEMS_SQL);
        assertThat(jdbc.callsTo(StockTransfers.HEADER_SQL).getFirst().params())
                .containsEntry("storeId", STORE).containsEntry("transferId", transferId);
    }

    @Test
    void itemsMovesRequestedQuantitiesAndReactivatesDeletedDestination() {
        jdbc.returns(StockTransfers.SOURCES_FOR_PRODUCTS_SQL, source(S1, P1, "100.00"), source(S2, P2, "200.00"))
                .returns(StockTransfers.DESTINATIONS_SQL, destination(D1, P1), destination(D2, P2))
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 5, false), locked(S2, FROM, P2, 2, false),
                        locked(D1, TO, P1, 4, false), locked(D2, TO, P2, 0, true));
        readBack("ITEMS", 4, 2);

        transfers.transfer(ACTOR, items(null, new TransferLine(P1, 3), new TransferLine(P2, 1)));

        assertThat(jdbc.callsTo(StockTransfers.SOURCES_FOR_PRODUCTS_SQL).getFirst().params())
                .containsEntry("productIds", List.of(P1, P2)).containsEntry("storeId", STORE);
        assertThat(jdbc.callsTo(StockTransfers.INSERT_LISTING_SQL)).isEmpty();
        assertThat(jdbc.callsTo(StockTransfers.REACTIVATE_LISTING_SQL)).singleElement()
                .satisfies(call -> assertThat(call.params()).containsEntry("inventoryId", D2)
                        .containsEntry("price", new BigDecimal("200.00")));
        assertThat(jdbc.callsTo(StockTransfers.INSERT_TRANSFER_SQL).getFirst().params())
                .containsEntry("mode", "ITEMS").containsEntry("totalUnits", 4).containsEntry("itemCount", 2)
                .containsEntry("notes", "reposición");
        List<StockChange> changes = appliedChanges();
        assertThat(changes).extracting(StockChange::inventoryId).containsExactly(S1, D1, S2, D2);
        assertThat(changes).extracting(StockChange::newStock).containsExactly(2, 7, 1, 1);
    }

    @Test
    void singleProductTransfer() {
        jdbc.returns(StockTransfers.SOURCES_FOR_PRODUCTS_SQL, source(S1, P1, "100.00"))
                .returns(StockTransfers.DESTINATIONS_SQL, destination(D1, P1))
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 5, false), locked(D1, TO, P1, 0, false));
        readBack("ITEMS", 5, 1);

        transfers.transfer(ACTOR, items(null, new TransferLine(P1, 5)));

        assertThat(jdbc.callsTo(StockTransfers.INSERT_ITEM_SQL)).singleElement()
                .satisfies(call -> assertThat(call.params()).containsEntry("quantity", 5));
        List<StockChange> changes = appliedChanges();
        assertThat(changes).extracting(StockChange::newStock).containsExactly(0, 5);
    }

    @Test
    void insufficientStockReportsEveryShortageAndChangesNothing() {
        jdbc.returns(StockTransfers.SOURCES_FOR_PRODUCTS_SQL, source(S1, P1, "100.00"), source(S2, P2, "200.00"))
                .returns(StockTransfers.DESTINATIONS_SQL)
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 5, false), locked(S2, FROM, P2, 2, false));

        assertThatThrownBy(() -> transfers.transfer(ACTOR,
                items(null, new TransferLine(P1, 10), new TransferLine(P2, 1))))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(error.getDetails()).isEqualTo(List.of(
                            Map.of("productId", P1, "requested", 10, "available", 5)));
                });

        assertNothingWritten();
    }

    @Test
    void productWithoutSourceListingIsNotFound() {
        jdbc.returns(StockTransfers.SOURCES_FOR_PRODUCTS_SQL, source(S1, P1, "100.00"));

        assertThatThrownBy(() -> transfers.transfer(ACTOR,
                items(null, new TransferLine(P1, 1), new TransferLine(P2, 1))))
                .isInstanceOfSatisfying(NotFoundException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("LISTING_NOT_FOUND");
                    assertThat(error.getDetails()).isEqualTo(List.of(Map.of("productId", P2)));
                });

        assertThat(jdbc.callsTo(StockTransfers.LOCK_SQL)).isEmpty();
        assertNothingWritten();
    }

    @Test
    void allWithoutStockIsEmpty() {
        assertThatThrownBy(() -> transfers.transfer(ACTOR, all()))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("TRANSFER_EMPTY"));
        assertNothingWritten();
    }

    @Test
    void allWhoseStockWasSoldBeforeTheLockIsEmpty() {
        jdbc.returns(StockTransfers.SOURCES_WITH_STOCK_SQL, source(S1, P1, "100.00"))
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 0, false));

        assertThatThrownBy(() -> transfers.transfer(ACTOR, all()))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("TRANSFER_EMPTY"));
        assertNothingWritten();
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingTransferWithoutWriting() {
        UUID existing = UUID.randomUUID();
        jdbc.returns(StockTransfers.FIND_BY_KEY_SQL, Map.of("id", existing))
                .returns(StockTransfers.HEADER_SQL, header(existing, "ITEMS", 3, 1))
                .returns(StockTransfers.ITEMS_SQL, Map.of("product_id", P1, "product_name", "Filtro",
                        "quantity", 3, "from_stock", 2, "to_stock", 3));

        TransferResult result = transfers.transfer(ACTOR, items("intento-1", new TransferLine(P1, 3)));

        assertThat(result.created()).isFalse();
        assertThat(result.transfer().id()).isEqualTo(existing);
        assertThat(result.transfer().items()).singleElement()
                .satisfies(item -> assertThat(item.toStock()).isEqualTo(3));
        assertThat(jdbc.callsTo(StockTransfers.IDEMPOTENCY_LOCK_SQL).getFirst().params().get("lockKey"))
                .isEqualTo("stock_transfers:" + STORE + ":intento-1");
        assertThat(jdbc.callsTo(StockTransfers.FIND_BY_KEY_SQL).getFirst().params())
                .containsEntry("storeId", STORE).containsEntry("idempotencyKey", "intento-1");
        assertThat(jdbc.callsTo(StockTransfers.SOURCES_FOR_PRODUCTS_SQL)).isEmpty();
        assertNothingWritten();
    }

    @Test
    void newIdempotencyKeyIsStoredInHeader() {
        jdbc.returns(StockTransfers.SOURCES_FOR_PRODUCTS_SQL, source(S1, P1, "100.00"))
                .returns(StockTransfers.DESTINATIONS_SQL, destination(D1, P1))
                .returns(StockTransfers.LOCK_SQL, locked(S1, FROM, P1, 5, false), locked(D1, TO, P1, 0, false));
        readBack("ITEMS", 1, 1);

        TransferResult result = transfers.transfer(ACTOR, items("intento-2", new TransferLine(P1, 1)));

        assertThat(result.created()).isTrue();
        assertThat(jdbc.sqlSequence()).startsWith(StockTransfers.IDEMPOTENCY_LOCK_SQL, StockTransfers.FIND_BY_KEY_SQL);
        assertThat(jdbc.callsTo(StockTransfers.INSERT_TRANSFER_SQL).getFirst().params())
                .containsEntry("idempotencyKey", "intento-2");
    }

    @Test
    void sameBranchIsRejectedBeforeTouchingTheDatabase() {
        TransferCommand command = new TransferCommand(STORE, FROM, FROM, StockTransfers.MODE_ALL, null, null, null);

        assertThatThrownBy(() -> transfers.transfer(ACTOR, command))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SAME_BRANCH"));
        verify(jdbc.client, never()).sql(anyString());
        verifyNoInteractions(ledger);
    }

    @Test
    void repeatedProductIsRejected() {
        assertThatThrownBy(() -> transfers.transfer(ACTOR,
                items(null, new TransferLine(P1, 1), new TransferLine(P1, 2))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("DUPLICATE_PRODUCT"));
        verify(jdbc.client, never()).sql(anyString());
    }

    @Test
    void listFiltersByBranchAndPages() {
        UUID id = UUID.randomUUID();
        jdbc.returns(StockTransfers.COUNT_BY_BRANCH_SQL, Map.of("total", 21L))
                .returns(StockTransfers.PAGE_BY_BRANCH_SQL, header(id, "ALL", 9, 3));

        var page = transfers.list(STORE, FROM, 2, 20);

        assertThat(page.total()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).singleElement().satisfies(row -> assertThat(row.id()).isEqualTo(id));
        assertThat(jdbc.callsTo(StockTransfers.PAGE_BY_BRANCH_SQL).getFirst().params())
                .containsEntry("storeId", STORE).containsEntry("branchId", FROM)
                .containsEntry("limit", 20).containsEntry("offset", 20L);
        assertThat(StockTransfers.PAGE_BY_BRANCH_SQL).contains("t.store_id = :storeId");
    }

    @Test
    void transferOfAnotherStoreIsNotFound() {
        assertThat(transfers.find(STORE, UUID.randomUUID())).isEmpty();
        assertThat(StockTransfers.HEADER_SQL).contains("t.store_id = :storeId").contains("t.id = :transferId");
        assertThat(jdbc.callsTo(StockTransfers.ITEMS_SQL)).isEmpty();
    }
}
