package com.autopartco.backend.core.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.autopartco.backend.core.notification.NotificationKind;
import com.autopartco.backend.core.notification.NotificationService;

@ExtendWith(MockitoExtension.class)
class SaleStatusServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SaleQueries saleQueries;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SaleStatusService saleStatusService;

    @Test
    void permitsOnlyValidOrderStatusTransitions() {
        assertTrue(SaleStatusService.isOrderTransitionAllowed("PENDING", "CONFIRMED"));
        assertTrue(SaleStatusService.isOrderTransitionAllowed("PENDING", "CANCELLED"));
        assertTrue(SaleStatusService.isOrderTransitionAllowed("CONFIRMED", "PREPARING"));
        assertFalse(SaleStatusService.isOrderTransitionAllowed("DELIVERED", "CANCELLED"));
        assertFalse(SaleStatusService.isOrderTransitionAllowed("PREPARING", "CONFIRMED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notifiesEveryCustomerAccountThroughNotificationService() {
        UUID saleId = UUID.randomUUID();
        UUID firstAccount = UUID.randomUUID();
        UUID secondAccount = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(saleId))).thenReturn(List.of(
                new SaleStatusService.CustomerAccount(firstAccount, orderId),
                new SaleStatusService.CustomerAccount(secondAccount, orderId)));

        saleStatusService.notifyCustomer(saleId, "Pedido actualizado", "Mensaje");

        // Cada aviso de estado lleva al detalle del pedido del comprador.
        verify(notificationService).notify(firstAccount, NotificationKind.ORDER_STATUS, "Pedido actualizado", "Mensaje",
                "/mis-pedidos/" + orderId);
        verify(notificationService).notify(secondAccount, NotificationKind.ORDER_STATUS, "Pedido actualizado",
                "Mensaje", "/mis-pedidos/" + orderId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsNotificationWhenCustomerHasNoAccount() {
        UUID saleId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(saleId))).thenReturn(List.of());

        saleStatusService.notifyCustomer(saleId, "Pedido actualizado", "Mensaje");

        verifyNoInteractions(notificationService);
    }
}
