package com.autopartco.backend.storesuite.dashboard;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.order.SaleQueries;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StorePermissionCodes;

@ExtendWith(MockitoExtension.class)
class StoreDashboardQueryTest {
    @Mock
    private StoreAccess storeAccess;

    @Mock
    private PlanEntitlements entitlements;

    @Mock
    private StoreApplications storeApplications;

    @Mock
    private SaleQueries saleQueries;

    @InjectMocks
    private StoreDashboardQuery storeDashboardQuery;

    @Test
    void checksStorePermissionBeforeLoadingDashboardData() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccess)
                .requirePermission("user@example.com", storeId, StorePermissionCodes.STORE_READ);

        assertThrows(
                AccessDeniedException.class,
                () -> storeDashboardQuery.execute(storeId, "user@example.com")
        );
        verifyNoInteractions(saleQueries, entitlements, storeApplications);
    }
}
