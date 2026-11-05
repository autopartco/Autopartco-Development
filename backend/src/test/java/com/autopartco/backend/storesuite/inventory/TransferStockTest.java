package com.autopartco.backend.storesuite.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.inventory.StockTransfers;
import com.autopartco.backend.core.inventory.StockTransfers.TransferCommand;
import com.autopartco.backend.core.inventory.StockTransfers.TransferItemView;
import com.autopartco.backend.core.inventory.StockTransfers.TransferLine;
import com.autopartco.backend.core.inventory.StockTransfers.TransferResult;
import com.autopartco.backend.core.inventory.StockTransfers.TransferView;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class TransferStockTest {
    private static final String EMAIL = "staff@example.com";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    private final UUID storeId = UUID.randomUUID();
    private final UUID fromBranch = UUID.randomUUID();
    private final UUID toBranch = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @Mock
    private BranchService branchService;

    @Mock
    private StockTransfers stockTransfers;

    @Mock
    private InventoryActor inventoryActor;

    private TransferStock transferStock() {
        return new TransferStock(storeAccess, storeService, branchService, stockTransfers, inventoryActor);
    }

    private TransferRequest itemsRequest(UUID to, String key) {
        return new TransferRequest(fromBranch, to, "ITEMS",
                List.of(new TransferRequest.Item(productId, 3)), "  reposición ", key);
    }

    private TransferView view() {
        return new TransferView(transferId, fromBranch, "Centro", toBranch, "Norte", "ITEMS", 3, 1, "reposición",
                "Ana Pérez", NOW, List.of(new TransferItemView(productId, "Filtro", 3, 2, 3)));
    }

    @Test
    void permissionDeniedTouchesNothing() {
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.INVENTORY_TRANSFER);

        assertThatThrownBy(() -> transferStock().execute(storeId, EMAIL, itemsRequest(toBranch, "k1")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(stockTransfers, branchService, inventoryActor);
    }

    @Test
    void validTransferChecksBothBranchesBindsActorAndReturnsCreated() {
        when(inventoryActor.bind(EMAIL)).thenReturn(actorId);
        when(stockTransfers.transfer(eq(actorId), any())).thenReturn(new TransferResult(view(), true));

        TransferStock.Outcome outcome = transferStock().execute(storeId, EMAIL, itemsRequest(toBranch, null));

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.transfer().id()).isEqualTo(transferId);
        assertThat(outcome.transfer().items()).singleElement()
                .satisfies(item -> assertThat(item.fromStock()).isEqualTo(2));
        ArgumentCaptor<TransferCommand> command = ArgumentCaptor.forClass(TransferCommand.class);
        InOrder order = inOrder(storeAccess, branchService, inventoryActor, stockTransfers);
        order.verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.INVENTORY_TRANSFER);
        order.verify(branchService).requireActiveInStore(storeId, fromBranch);
        order.verify(branchService).requireActiveInStore(storeId, toBranch);
        order.verify(inventoryActor).bind(EMAIL);
        order.verify(stockTransfers).transfer(eq(actorId), command.capture());
        assertThat(command.getValue()).isEqualTo(new TransferCommand(storeId, fromBranch, toBranch, "ITEMS",
                List.of(new TransferLine(productId, 3)), "reposición", null));
    }

    @Test
    void allModeSendsNoLines() {
        when(inventoryActor.bind(EMAIL)).thenReturn(actorId);
        when(stockTransfers.transfer(eq(actorId), any())).thenReturn(new TransferResult(view(), true));

        transferStock().execute(storeId, EMAIL, new TransferRequest(fromBranch, toBranch, "ALL", null, " ", " "));

        ArgumentCaptor<TransferCommand> command = ArgumentCaptor.forClass(TransferCommand.class);
        verify(stockTransfers).transfer(eq(actorId), command.capture());
        assertThat(command.getValue().mode()).isEqualTo("ALL");
        assertThat(command.getValue().items()).isEmpty();
        assertThat(command.getValue().notes()).isNull();
        assertThat(command.getValue().idempotencyKey()).isNull();
    }

    @Test
    void sameBranchIsBadRequest() {
        assertThatThrownBy(() -> transferStock().execute(storeId, EMAIL, itemsRequest(fromBranch, null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SAME_BRANCH"));

        verifyNoInteractions(branchService, inventoryActor);
        verify(stockTransfers, never()).transfer(any(), any());
    }

    @Test
    void branchOfAnotherStoreIsNotFound() {
        // La sucursal destino es de otro almacén; la de origen es válida.
        org.mockito.Mockito.lenient()
                .doThrow(new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal."))
                .when(branchService).requireActiveInStore(storeId, toBranch);

        assertThatThrownBy(() -> transferStock().execute(storeId, EMAIL, itemsRequest(toBranch, null)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("BRANCH_NOT_FOUND"));

        verifyNoInteractions(inventoryActor);
        verify(stockTransfers, never()).transfer(any(), any());
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingWithoutRevalidating() {
        when(stockTransfers.findIdByKey(storeId, "k1")).thenReturn(Optional.of(transferId));
        when(stockTransfers.find(storeId, transferId)).thenReturn(Optional.of(view()));

        TransferStock.Outcome outcome = transferStock().execute(storeId, EMAIL, itemsRequest(toBranch, " k1 "));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.transfer().id()).isEqualTo(transferId);
        verifyNoInteractions(branchService, inventoryActor);
        verify(stockTransfers, never()).transfer(any(), any());
    }

    @Test
    void listRequiresReadPermissionAndLimitsPageSize() {
        ListTransfers list = new ListTransfers(storeAccess, stockTransfers);

        assertThatThrownBy(() -> list.list(storeId, EMAIL, null, 1, 101))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR"));

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_READ);
        verify(stockTransfers, never()).list(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void detailOfAnotherStoreIsNotFound() {
        ListTransfers list = new ListTransfers(storeAccess, stockTransfers);
        when(stockTransfers.find(storeId, transferId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> list.get(storeId, EMAIL, transferId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("TRANSFER_NOT_FOUND"));
    }

    @Test
    void readPermissionDeniedOnDetail() {
        ListTransfers list = new ListTransfers(storeAccess, stockTransfers);
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.PRODUCT_READ);

        assertThatThrownBy(() -> list.get(storeId, EMAIL, transferId)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stockTransfers);
    }
}
