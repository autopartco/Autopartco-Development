package com.autopartco.backend.seller.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.StorePermissionCodes;

@ExtendWith(MockitoExtension.class)
class SellerDashboardServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StoreAccess storeAccessService;

    @Mock
    private PlanEntitlements entitlementService;

    @Mock
    private StoreApplicationRepository applicationRepository;

    @InjectMocks
    private SellerDashboardService dashboardService;

    @Test
    void permitsOnlyValidOrderStatusTransitions() {
        assertTrue(SellerDashboardService.isOrderTransitionAllowed("PENDING", "CONFIRMED"));
        assertTrue(SellerDashboardService.isOrderTransitionAllowed("PENDING", "CANCELLED"));
        assertTrue(SellerDashboardService.isOrderTransitionAllowed("CONFIRMED", "PREPARING"));
        assertFalse(SellerDashboardService.isOrderTransitionAllowed("DELIVERED", "CANCELLED"));
        assertFalse(SellerDashboardService.isOrderTransitionAllowed("PREPARING", "CONFIRMED"));
    }

    @Test
    void permitsOnlyForwardShipmentStatusTransitions() {
        assertTrue(SellerDashboardService.isShipmentTransitionAllowed("PREPARING", "DISPATCHED"));
        assertTrue(SellerDashboardService.isShipmentTransitionAllowed("IN_TRANSIT", "DELIVERED"));
        assertFalse(SellerDashboardService.isShipmentTransitionAllowed("PREPARING", "DELIVERED"));
        assertFalse(SellerDashboardService.isShipmentTransitionAllowed("DELIVERED", "IN_TRANSIT"));
    }

    @Test
    void checksStorePermissionBeforeLoadingDashboardData() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccessService)
                .requirePermission("user@example.com", storeId, StorePermissionCodes.STORE_READ);

        assertThrows(
                AccessDeniedException.class,
                () -> dashboardService.dashboard(storeId, "user@example.com")
        );
        verifyNoInteractions(jdbcTemplate, entitlementService, applicationRepository);
    }

    @Test
    void checksOrderPermissionBeforeLoadingSellerOrders() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccessService)
                .requirePermission("user@example.com", storeId, StorePermissionCodes.ORDER_READ);

        assertThrows(
                AccessDeniedException.class,
                () -> dashboardService.orders(storeId, "user@example.com", null, 0, 20)
        );
        verify(storeAccessService).requirePermission(
                "user@example.com",
                storeId,
                StorePermissionCodes.ORDER_READ
        );
        verifyNoInteractions(jdbcTemplate);
    }
}
