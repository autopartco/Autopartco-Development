package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.order.RecordingJdbcClient.Statement;

class OrderPlacementAwaitingPaymentTest {
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID SALE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-04T15:30:00Z");

    @Test
    void placesOrderAwaitingPaymentWithPendingWompiPayment() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(statement -> {
            if (statement.sql().equals(OrderPlacement.INSERT_ORDER_SQL)) {
                return ORDER_ID;
            }
            if (statement.sql().equals(OrderPlacement.INSERT_SALE_SQL)) {
                return SALE_ID;
            }
            return PAYMENT_ID;
        });
        NewOrder order = new NewOrder(PERSON_ID,
                new NewOrder.DeliveryAddress("Calle 10", null, "Bogotá", "Cundinamarca", "Colombia", null, null, null),
                "WOMPI", "ignored",
                List.of(new NewOrder.NewSale(UUID.randomUUID(), new BigDecimal("230000.00"), new BigDecimal("0.00"),
                        new BigDecimal("230000.00"), List.of(new NewOrder.NewLine(UUID.randomUUID(),
                                UUID.randomUUID(), 2, new BigDecimal("115000.00"))))));

        UUID orderId = new OrderPlacement(jdbc.client()).placeAwaitingPayment(order, new PendingPayment(
                "WOMPI", "APM-abc", "6f1c2c1e-7d3b-4c55-9a43-1b2f0c9e5a10", "SANDBOX", EXPIRES_AT));

        assertThat(orderId).isEqualTo(ORDER_ID);
        List<Statement> statements = jdbc.statements();
        assertThat(statements).extracting(Statement::sql).containsExactly(
                OrderPlacement.INSERT_ORDER_SQL,
                OrderPlacement.INSERT_ORDER_ADDRESS_SQL,
                OrderPlacement.INSERT_SALE_SQL,
                OrderPlacement.INSERT_ORDER_DETAIL_SQL,
                OrderPlacement.INSERT_PENDING_PAYMENT_SQL,
                OrderPlacement.INSERT_PAYMENT_EVENT_SQL);
        assertThat(statements.get(0).param("stateCode")).isEqualTo("AWAITING_PAYMENT");
        assertThat(statements.get(2).param("stateCode")).isEqualTo("PENDING");

        Statement payment = statements.get(4);
        assertThat(payment.sql()).doesNotContain("paid_at");
        assertThat(payment.params()).containsAllEntriesOf(Map.of(
                "orderId", ORDER_ID,
                "methodCode", "WOMPI",
                "statusCode", "PENDING",
                "provider", "WOMPI",
                "reference", "APM-abc",
                "amount", new BigDecimal("230000.00"),
                "currency", "COP",
                "idempotencyKey", "6f1c2c1e-7d3b-4c55-9a43-1b2f0c9e5a10",
                "environment", "SANDBOX",
                "expiresAt", OffsetDateTime.ofInstant(EXPIRES_AT, ZoneOffset.UTC)));
        assertThat(statements.get(5).param("paymentId")).isEqualTo(PAYMENT_ID);
        assertThat(statements.get(5).param("statusCode")).isEqualTo("PENDING");
    }

    @Test
    void findsOwnerByIdempotencyKeyAndListsMethodsByPrefix() {
        UUID orderId = UUID.randomUUID();
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement ->
                statement.sql().equals(OrderPlacement.IDEMPOTENCY_KEY_OWNER_SQL)
                        ? List.of(RecordingJdbcClient.row(Map.of("order_id", orderId, "person_id", PERSON_ID)))
                        : List.of(RecordingJdbcClient.row(Map.of("code", "SIMULATED_CARD", "name", "Tarjeta"))));
        OrderPlacement placement = new OrderPlacement(jdbc.client());

        assertThat(placement.findByIdempotencyKey("key-1")).contains(new PaymentReferenceOwner(orderId, PERSON_ID));
        assertThat(placement.activePaymentMethods("SIMULATED_"))
                .containsExactly(new PaymentMethodOption("SIMULATED_CARD", "Tarjeta"));
        assertThat(jdbc.statements().get(0).param("idempotencyKey")).isEqualTo("key-1");
        assertThat(jdbc.statements().get(1).param("prefix")).isEqualTo("SIMULATED_");
        assertThat(jdbc.statements().get(1).sql()).contains("active = true").contains("deleted_at IS NULL");
    }
}
