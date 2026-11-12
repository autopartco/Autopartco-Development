package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.events.SaleDelivered;

/** Una venta que pasa a DELIVERED publica el evento que dispara la invitación a calificar; otros estados no. */
class SaleDeliveredEventTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final List<Object> published = new ArrayList<>();
    private final SaleStatusService service = new SaleStatusService(jdbcTemplate, mock(SaleQueries.class),
            mock(NotificationService.class), published::add);
    private final UUID saleId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @Test
    void deliveredSalePublishesTheEventWithItsOrder() {
        when(jdbcTemplate.update(anyString(), eq("DELIVERED"), eq(saleId))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), eq(saleId))).thenReturn(orderId);

        service.updateSaleStatus(saleId, "DELIVERED");

        assertThat(published).containsExactly(new SaleDelivered(saleId, orderId));
    }

    @Test
    void otherStatusesPublishNothing() {
        when(jdbcTemplate.update(anyString(), any(Object.class), any(Object.class))).thenReturn(1);

        service.updateSaleStatus(saleId, "SHIPPED");

        assertThat(published).isEmpty();
    }
}
