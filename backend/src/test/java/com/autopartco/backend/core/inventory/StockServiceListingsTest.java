package com.autopartco.backend.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.inventory.internal.Inventory;
import com.autopartco.backend.core.inventory.internal.InventoryRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class StockServiceListingsTest {
    private final UUID storeId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final BigDecimal price = new BigDecimal("85000.00");

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private StockService stockService;

    @Test
    void duplicateListingIsConflict() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of(mock(Inventory.class)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> stockService.addListing(branchId, productId, 5, price));

        assertEquals("LISTING_ALREADY_EXISTS", error.getCode());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void softDeletedListingIsRestoredInsteadOfInserted() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of());
        when(inventoryRepository.restoreListing(branchId, productId, price, 5)).thenReturn(1);

        stockService.addListing(branchId, productId, 5, price);

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void newListingIsInsertedWhenNothingToRestore() {
        when(inventoryRepository.findListings(branchId, productId)).thenReturn(List.of());
        when(inventoryRepository.restoreListing(branchId, productId, price, 0)).thenReturn(0);

        stockService.addListing(branchId, productId, 0, price);

        verify(inventoryRepository).save(any(Inventory.class));
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
