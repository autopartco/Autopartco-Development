package com.autopartco.backend.storesuite.orders;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.shared.error.NotFoundException;

@ExtendWith(MockitoExtension.class)
class ListStoreOrdersTest {
    @Mock
    private StoreAccess storeAccess;

    @Mock
    private SaleQueries saleQueries;

    @Mock
    private BranchService branchService;

    @InjectMocks
    private ListStoreOrders listStoreOrders;

    @Test
    void checksOrderPermissionBeforeLoadingSellerOrders() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess)
                .requirePermission("user@example.com", storeId, StorePermissionCodes.ORDER_READ);

        assertThrows(
                AccessDeniedException.class,
                () -> listStoreOrders.execute(storeId, "user@example.com", null, 0, 20)
        );
        verify(storeAccess).requirePermission(
                "user@example.com",
                storeId,
                StorePermissionCodes.ORDER_READ
        );
        verifyNoInteractions(saleQueries);
    }

    @Test
    void rejectsBranchFromAnotherStoreBeforeQuerying() {
        UUID storeId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        doThrow(new NotFoundException("BRANCH_NOT_FOUND", "No se encontró la sucursal."))
                .when(branchService).requireInStore(storeId, branchId);

        assertThrows(
                NotFoundException.class,
                () -> listStoreOrders.execute(storeId, "user@example.com", null, branchId, 0, 20)
        );
        verifyNoInteractions(saleQueries);
    }

    @Test
    void filtersByBranchWhenGiven() {
        UUID storeId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        listStoreOrders.execute(storeId, "user@example.com", "PENDING", branchId, 1, 20);

        verify(branchService).requireInStore(storeId, branchId);
        verify(saleQueries).list(storeId, "PENDING", branchId, 1, 20);
    }
}
