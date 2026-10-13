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
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

@ExtendWith(MockitoExtension.class)
class ListStoreOrdersTest {
    @Mock
    private StoreAccess storeAccess;

    @Mock
    private SaleQueries saleQueries;

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
}
