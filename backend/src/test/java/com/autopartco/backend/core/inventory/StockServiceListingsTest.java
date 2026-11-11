package com.autopartco.backend.core.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.inventory.StockLedger.StockChange;
import com.autopartco.backend.core.inventory.internal.Inventory;
import com.autopartco.backend.core.inventory.internal.InventoryRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class StockServiceListingsTest {
    private final UUID storeId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID inventoryId = UUID.randomUUID();
    private final BigDecimal price = new BigDecimal("85000.00");

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private StockLedger stockLedger;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    @InjectMocks
    private StockService stockService;

    private Inventory listing() {
        Inventory inventory = Inventory.builder().branchId(branchId).productId(productId).stock(4).price(price).build();
        inventory.setId(inventoryId);
        return inventory;
    }

    @SuppressWarnings("unchecked")
    private StockChange singleChange() {
        ArgumentCaptor<List<StockChange>> changes = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).apply(isNull(), eq(StockLedger.SOURCE_USER), changes.capture());
        assertThat(changes.getValue()).hasSize(1);
        return changes.getValue().getFirst();
    }

    @Test
    void duplicateListingIsConflict() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of(mock(Inventory.class)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> stockService.addListing(branchId, productId, 5, price));

        assertEquals("LISTING_ALREADY_EXISTS", error.getCode());
        verify(inventoryRepository, never()).save(any());
        verify(inventoryRepository, never()).saveAndFlush(any());
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }

    @Test
    void softDeletedListingIsRestoredAndStockGoesThroughLedger() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of(), List.of(listing()));
        when(inventoryRepository.restoreListing(branchId, productId, price)).thenReturn(1);

        stockService.addListing(branchId, productId, 5, price);

        verify(inventoryRepository, never()).saveAndFlush(any());
        StockChange change = singleChange();
        assertEquals(inventoryId, change.inventoryId());
        assertEquals(5, change.newStock());
        assertEquals(StockLedger.REASON_MANUAL_CORRECTION, change.reasonCode());
    }

    @Test
    void newListingIsInsertedWithZeroStockAndInitialLoadGoesThroughLedger() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of());
        when(inventoryRepository.restoreListing(branchId, productId, price)).thenReturn(0);
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenReturn(listing());

        stockService.addListing(branchId, productId, 7, price);

        ArgumentCaptor<Inventory> saved = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).saveAndFlush(saved.capture());
        assertEquals(0, saved.getValue().getStock());
        StockChange change = singleChange();
        assertEquals(7, change.newStock());
        assertEquals(StockLedger.REASON_INITIAL_LOAD, change.reasonCode());
    }

    @Test
    void createListingRecordsInitialLoad() {
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenReturn(listing());

        stockService.createListing(branchId, productId, 3, price);

        StockChange change = singleChange();
        assertEquals(inventoryId, change.inventoryId());
        assertEquals(3, change.newStock());
        assertEquals(StockLedger.REASON_INITIAL_LOAD, change.reasonCode());
    }

    @Test
    void updateListingWritesPriceFirstAndStockAsManualCorrection() {
        Inventory inventory = listing();
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of(inventory));

        stockService.updateListing(branchId, productId, price, 9);

        InOrder order = inOrder(inventoryRepository, stockLedger);
        order.verify(inventoryRepository).updatePrice(inventoryId, price);
        order.verify(stockLedger).apply(isNull(), eq(StockLedger.SOURCE_USER), anyList());
        StockChange change = singleChange();
        assertEquals(9, change.newStock());
        assertEquals(StockLedger.REASON_MANUAL_CORRECTION, change.reasonCode());
        // La entidad no se modifica: un flush pisaría el stock y la versión que cambió el ledger.
        assertEquals(4, inventory.getStock());
    }

    @Test
    void updateListingWithoutStockDoesNotTouchLedger() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of(listing()));

        stockService.updateListing(branchId, productId, price, null);

        verify(inventoryRepository).updatePrice(inventoryId, price);
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }

    @Test
    void updateMissingListingIsNotFound() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> stockService.updateListing(branchId, productId, null, 2));

        assertEquals("PRODUCT_NOT_FOUND", error.getCode());
        verify(stockLedger, never()).apply(any(), any(), anyList());
    }

    @Test
    void removingMissingListingIsNotFound() {
        when(inventoryRepository.softDeleteListing(storeId, branchId, productId)).thenReturn(0);

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> stockService.removeListing(storeId, branchId, productId));

        assertEquals("LISTING_NOT_FOUND", error.getCode());
    }

    @Test
    void deletingTwiceIsNotFoundTheSecondTime() {
        when(inventoryRepository.softDeleteListing(storeId, branchId, productId)).thenReturn(1, 0);

        stockService.removeListing(storeId, branchId, productId);
        NotFoundException error = assertThrows(NotFoundException.class,
                () -> stockService.removeListing(storeId, branchId, productId));

        assertEquals("LISTING_NOT_FOUND", error.getCode());
    }

    @Test
    void branchOfAnotherStoreIsNotFound() {
        UUID otherStore = UUID.randomUUID();
        when(inventoryRepository.softDeleteListing(otherStore, branchId, productId)).thenReturn(0);

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> stockService.removeListing(otherStore, branchId, productId));

        assertEquals("LISTING_NOT_FOUND", error.getCode());
    }
}
