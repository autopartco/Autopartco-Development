package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.ConflictException;

/** Ventas de pedidos en espera del pago Wompi: ocultas al almacén y sin cambios de estado (ADR 0004). */
class SaleAwaitingPaymentTest {
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final UUID SALE_ID = UUID.randomUUID();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JdbcTemplate lockedSale(String saleStatus, String orderStatus) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(eq(SaleStatusService.LOCK_SALE_SQL), any(RowMapper.class), eq(STORE_ID), eq(SALE_ID)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet result = mock(ResultSet.class);
                    when(result.getString("sale_status")).thenReturn(saleStatus);
                    when(result.getString("order_status")).thenReturn(orderStatus);
                    return List.of(mapper.mapRow(result, 0));
                });
        return jdbcTemplate;
    }

    @Test
    void statusChangeOfUnpaidOrderIsRejectedWith409() {
        JdbcTemplate jdbcTemplate = lockedSale("PENDING", "AWAITING_PAYMENT");
        SaleQueries saleQueries = mock(SaleQueries.class);
        NotificationService notifications = mock(NotificationService.class);
        SaleStatusService service = new SaleStatusService(jdbcTemplate, saleQueries, notifications);

        assertThatThrownBy(() -> service.updateStatus(STORE_ID, SALE_ID, new SaleStatusRequest("CONFIRMED")))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("SALE_AWAITING_PAYMENT");
                    assertThat(error.getMessage()).isEqualTo("El pedido todavía no está pagado.");
                });
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(saleQueries, notifications);
    }

    @Test
    void paidOrderReturnsSaleStatus() {
        SaleStatusService service = new SaleStatusService(lockedSale("PENDING", "CONFIRMED"),
                mock(SaleQueries.class), mock(NotificationService.class));

        assertThat(service.lockSaleStatus(STORE_ID, SALE_ID)).isEqualTo("PENDING");
        assertThat(SaleStatusService.LOCK_SALE_SQL).contains("FOR UPDATE OF sale").contains("order_state.code");
    }

    @Test
    @SuppressWarnings("unchecked")
    void storeListDetailAndSummaryHideUnpaidOrders() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(sql.capture(), any(RowMapper.class), any(Object[].class))).thenReturn(null);
        SaleQueries queries = new SaleQueries(jdbcTemplate);

        queries.list(STORE_ID, null, 0, 20);
        queries.list(STORE_ID, "PENDING", 0, 20);
        queries.monthlySummary(STORE_ID);
        try {
            queries.detail(STORE_ID, SALE_ID);
        } catch (RuntimeException expected) {
            // Sin filas: SELLER_ORDER_NOT_FOUND, igual que una venta ajena.
        }

        assertThat(sql.getAllValues()).hasSize(4).allSatisfy(statement -> assertThat(statement)
                .contains("code = 'AWAITING_PAYMENT'")
                .contains("cancellation_reason_code IS DISTINCT FROM 'PAYMENT_TIMEOUT'"));
        assertThat(SaleQueries.MONTHLY_SUMMARY_SQL).contains("JOIN orders customer_order");
    }
}
