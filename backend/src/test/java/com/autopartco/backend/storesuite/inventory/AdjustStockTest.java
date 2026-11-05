package com.autopartco.backend.storesuite.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.inventory.InventoryQueries;
import com.autopartco.backend.core.inventory.InventoryQueries.ListingRef;
import com.autopartco.backend.core.inventory.InventoryQueries.MovementView;
import com.autopartco.backend.core.inventory.InventoryQueries.ReasonView;
import com.autopartco.backend.core.inventory.StockLedger;
import com.autopartco.backend.core.inventory.StockLedger.LockedListing;
import com.autopartco.backend.core.inventory.StockLedger.MovementRow;
import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class AdjustStockTest {
    private static final String EMAIL = "staff@example.com";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    private final UUID storeId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID inventoryId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID movementId = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @Mock
    private BranchService branchService;

    @Mock
    private InventoryQueries inventoryQueries;

    @Mock
    private StockLedger stockLedger;

    @Mock
    private InventoryActor inventoryActor;

    @InjectMocks
    private AdjustStock adjustStock;

    private StockAdjustmentRequest request(String mode, int quantity, String reason) {
        return new StockAdjustmentRequest(branchId, productId, mode, quantity, reason, "  conteo de cierre ");
    }

    private void listingWithStock(int stock, String reasonCode, String direction) {
        when(inventoryQueries.listing(storeId, branchId, productId))
                .thenReturn(Optional.of(new ListingRef(inventoryId, stock)));
        when(inventoryQueries.reasons(List.of(reasonCode)))
                .thenReturn(List.of(new ReasonView(reasonCode, "Motivo", direction)));
        when(inventoryActor.bind(EMAIL)).thenReturn(actorId);
        when(stockLedger.lock(List.of(inventoryId)))
                .thenReturn(List.of(new LockedListing(inventoryId, branchId, productId, stock)));
    }

    private void ledgerWrites(String type, int quantity, int previous, int next, String reason) {
        when(stockLedger.apply(eq(actorId), eq(StockLedger.SOURCE_USER), anyList())).thenReturn(List.of(
                new MovementRow(movementId, inventoryId, branchId, productId, type, quantity, previous, next, reason,
                        NOW)));
        when(inventoryQueries.movement(storeId, movementId)).thenReturn(Optional.of(new MovementView(movementId, NOW,
                branchId, "Centro", productId, "Filtro", "SKU-1", type, quantity, previous, next, reason, "Motivo",
                "Ana Pérez", null, null, "conteo de cierre")));
    }

    @SuppressWarnings("unchecked")
    private StockChange appliedChange() {
        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(eq(actorId), eq(StockLedger.SOURCE_USER), changes.capture());
        assertThat(changes.getValue()).hasSize(1);
        return changes.getValue().getFirst();
    }

    @Test
    void permissionIsCheckedBeforeReadingOrWriting() {
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.INVENTORY_ADJUST);

        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("SET", 3, "COUNT_ADJUSTMENT")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(inventoryQueries, stockLedger, inventoryActor, branchService);
    }

    @Test
    void setWritesMovementWithReasonActorAndNotes() {
        listingWithStock(5, "COUNT_ADJUSTMENT", "ANY");
        ledgerWrites("IN", 3, 5, 8, "COUNT_ADJUSTMENT");

        MovementResponse response = adjustStock.execute(storeId, EMAIL, request("SET", 8, "COUNT_ADJUSTMENT"));

        StockChange change = appliedChange();
        assertThat(change.inventoryId()).isEqualTo(inventoryId);
        assertThat(change.newStock()).isEqualTo(8);
        assertThat(change.reasonCode()).isEqualTo("COUNT_ADJUSTMENT");
        assertThat(change.notes()).isEqualTo("conteo de cierre");
        assertThat(response.id()).isEqualTo(movementId);
        assertThat(response.actorName()).isEqualTo("Ana Pérez");
        InOrder order = inOrder(storeAccess, branchService, stockLedger);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.INVENTORY_ADJUST);
        order.verify(branchService).requireActiveInStore(storeId, branchId);
        order.verify(stockLedger).lock(List.of(inventoryId));
        order.verify(stockLedger).apply(eq(actorId), eq(StockLedger.SOURCE_USER), anyList());
    }

    @Test
    void negativeDeltaSubtractsFromLockedStock() {
        listingWithStock(5, "DAMAGE", "OUT");
        ledgerWrites("OUT", 2, 5, 3, "DAMAGE");

        adjustStock.execute(storeId, EMAIL, request("DELTA", -2, "DAMAGE"));

        assertThat(appliedChange().newStock()).isEqualTo(3);
    }

    @Test
    void deltaBelowZeroIsInsufficientStock() {
        listingWithStock(2, "LOSS_THEFT", "OUT");

        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("DELTA", -3, "LOSS_THEFT")))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(error.getDetails().toString()).contains("available=2").contains("requested=3");
                });
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }

    @Test
    void reasonDirectionMustMatch() {
        listingWithStock(5, "DAMAGE", "OUT");

        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("DELTA", 2, "DAMAGE")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REASON_DIRECTION"));
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }

    @Test
    void nonAdjustableReasonIsRejectedBeforeQueries() {
        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("DELTA", 2, "TRANSFER_IN")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_REASON"));
        verifyNoInteractions(inventoryQueries, stockLedger);
    }

    @Test
    void zeroDeltaAndNegativeSetAreInvalidQuantity() {
        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("DELTA", 0, "MANUAL_CORRECTION")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_QUANTITY"));
        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("SET", -1, "MANUAL_CORRECTION")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_QUANTITY"));
        verifyNoInteractions(stockLedger);
    }

    @Test
    void listingOfAnotherStoreIsNotFound() {
        when(inventoryQueries.listing(storeId, branchId, productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("SET", 4, "COUNT_ADJUSTMENT")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_FOUND"));
        verifyNoInteractions(stockLedger, inventoryActor);
    }

    @Test
    void sameStockIsNoChanges() {
        listingWithStock(4, "COUNT_ADJUSTMENT", "ANY");

        assertThatThrownBy(() -> adjustStock.execute(storeId, EMAIL, request("SET", 4, "COUNT_ADJUSTMENT")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("NO_CHANGES"));
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }
}
