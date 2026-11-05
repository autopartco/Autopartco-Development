package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.order.RecordingJdbcClient.Statement;

class OrderPaymentsTest {
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final String REFERENCE = "APM-3f9a0c2b7d6e4f1a9b8c7d6e5f4a3b2c";

    private static Map<String, Object> paymentRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("payment_id", PAYMENT_ID);
        row.put("order_id", ORDER_ID);
        row.put("person_id", PERSON_ID);
        row.put("order_number", 1042L);
        row.put("order_status", "AWAITING_PAYMENT");
        row.put("provider", "WOMPI");
        row.put("provider_reference", REFERENCE);
        row.put("payment_status", "PENDING");
        row.put("provider_status", null);
        row.put("provider_transaction_id", null);
        row.put("amount", new BigDecimal("230000.00"));
        row.put("currency", "COP");
        row.put("environment", "SANDBOX");
        row.put("expires_at", OffsetDateTime.of(2026, 11, 4, 15, 30, 0, 0, ZoneOffset.UTC));
        row.put("paid_at", Timestamp.valueOf(LocalDateTime.of(2026, 11, 4, 10, 5)));
        row.put("requires_manual_refund", Boolean.FALSE);
        return row;
    }

    @Test
    void lockByReferenceLocksPaymentFirstThenReadsFreshStateAndLocksOrder() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement -> {
            if (statement.sql().equals(OrderPayments.LOCK_PAYMENT_SQL)) {
                return List.of(RecordingJdbcClient.row(Map.of("id", PAYMENT_ID)));
            }
            return List.of(RecordingJdbcClient.row(paymentRow()));
        });

        Optional<OrderPayment> payment = new OrderPayments(jdbc.client()).lockByReference("WOMPI", REFERENCE);

        List<Statement> statements = jdbc.statements();
        assertThat(statements).extracting(Statement::sql)
                .containsExactly(OrderPayments.LOCK_PAYMENT_SQL, OrderPayments.LOCKED_PAYMENT_SQL);
        assertThat(statements.get(0).sql()).contains("FOR UPDATE");
        assertThat(statements.get(0).param("provider")).isEqualTo("WOMPI");
        assertThat(statements.get(0).param("reference")).isEqualTo(REFERENCE);
        assertThat(statements.get(1).sql()).contains("FOR UPDATE OF customer_order");
        assertThat(statements.get(1).param("paymentId")).isEqualTo(PAYMENT_ID);

        assertThat(payment).get().satisfies(found -> {
            assertThat(found.orderId()).isEqualTo(ORDER_ID);
            assertThat(found.orderStatus()).isEqualTo("AWAITING_PAYMENT");
            assertThat(found.status()).isEqualTo("PENDING");
            assertThat(found.amount()).isEqualByComparingTo("230000.00");
            assertThat(found.expiresAt()).isEqualTo(Instant.parse("2026-11-04T15:30:00Z"));
            assertThat(found.paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 10, 5));
        });
    }

    @Test
    void lockByUnknownReferenceIsEmpty() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient();

        assertThat(new OrderPayments(jdbc.client()).lockByReference("WOMPI", REFERENCE)).isEmpty();
        assertThat(jdbc.statements()).hasSize(1);
    }

    @Test
    void buyerReadFiltersByOwnerAndTakesLatestPayment() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient()
                .onRows(statement -> List.of(RecordingJdbcClient.row(paymentRow())));

        assertThat(new OrderPayments(jdbc.client()).findForBuyer(PERSON_ID, ORDER_ID)).isPresent();
        Statement statement = jdbc.statements().getFirst();
        assertThat(statement.sql()).contains("customer_order.person_id = :personId")
                .contains("ORDER BY payment.created_at DESC").contains("LIMIT 1").doesNotContain("FOR UPDATE");
        assertThat(statement.param("personId")).isEqualTo(PERSON_ID);
        assertThat(statement.param("orderId")).isEqualTo(ORDER_ID);
    }

    @Test
    void approvalStoresTransactionAndEventWithoutPaymentEventSource() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient();

        new OrderPayments(jdbc.client()).markApproved(PAYMENT_ID, "t-1", "APPROVED", true, "Pago Wompi aprobado");

        List<Statement> statements = jdbc.statements();
        assertThat(statements).extracting(Statement::sql)
                .containsExactly(OrderPayments.APPROVE_PAYMENT_SQL, OrderPayments.INSERT_PAYMENT_EVENT_SQL);
        assertThat(statements.get(0).sql()).contains("paid_at = now()").contains("'APPROVED'");
        assertThat(statements.get(0).param("transactionId")).isEqualTo("t-1");
        assertThat(statements.get(0).param("providerStatus")).isEqualTo("APPROVED");
        assertThat(statements.get(0).param("requiresManualRefund")).isEqualTo(true);
        assertThat(statements.get(1).param("statusCode")).isEqualTo("APPROVED");
        assertThat(OrderPayments.INSERT_PAYMENT_EVENT_SQL).doesNotContain("source");
    }

    @Test
    void failureStoresMappedStatusAndRawProviderStatus() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient();

        new OrderPayments(jdbc.client()).markFailed(PAYMENT_ID, "REJECTED", "t-1", "DECLINED", "Pago Wompi DECLINED");

        Statement update = jdbc.statements().getFirst();
        assertThat(update.param("statusCode")).isEqualTo("REJECTED");
        assertThat(update.param("providerStatus")).isEqualTo("DECLINED");
        assertThat(jdbc.statements().get(1).param("statusCode")).isEqualTo("REJECTED");
    }

    @Test
    void confirmAndCancelOnlyMoveOrdersAwaitingPayment() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient();
        OrderPayments payments = new OrderPayments(jdbc.client());

        assertThat(payments.confirmAwaitingOrder(ORDER_ID)).isTrue();
        assertThat(payments.cancelAwaitingOrder(ORDER_ID, "PAYMENT_TIMEOUT")).isTrue();

        List<Statement> statements = jdbc.statements();
        assertThat(statements).extracting(Statement::sql).containsExactly(
                OrderPayments.CONFIRM_ORDER_SQL, OrderPayments.CANCEL_ORDER_SQL, OrderPayments.CANCEL_SALES_SQL);
        assertThat(OrderPayments.CONFIRM_ORDER_SQL).contains("code = 'AWAITING_PAYMENT'");
        assertThat(OrderPayments.CANCEL_ORDER_SQL).contains("code = 'AWAITING_PAYMENT'")
                .contains("cancellation_reason_code = :reasonCode").contains("cancelled_at = now()");
        assertThat(statements.get(1).param("reasonCode")).isEqualTo("PAYMENT_TIMEOUT");
        assertThat(OrderPayments.CANCEL_SALES_SQL).contains("code = 'PENDING'");
    }

    @Test
    void expirationIsGuardedAndSkipsLockedRows() {
        OffsetDateTime expiresAt = OffsetDateTime.of(2026, 11, 4, 15, 30, 0, 0, ZoneOffset.UTC);
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement -> List.of(RecordingJdbcClient.row(
                Map.of("payment_id", PAYMENT_ID, "order_id", ORDER_ID, "provider_reference", REFERENCE,
                        "environment", "SANDBOX", "expires_at", expiresAt))));
        OrderPayments payments = new OrderPayments(jdbc.client());

        OrderPayments.ExpiredCandidate first = new OrderPayments.ExpiredCandidate(PAYMENT_ID, ORDER_ID, REFERENCE,
                "SANDBOX", expiresAt.toInstant());
        assertThat(payments.findExpired("WOMPI", null, 50)).containsExactly(first);
        assertThat(payments.lockExpired(PAYMENT_ID)).contains(new OrderPayments.ExpiredPayment(PAYMENT_ID, ORDER_ID));
        assertThat(payments.markExpired(PAYMENT_ID, "Pago vencido")).isTrue();

        assertThat(OrderPayments.EXPIRED_CANDIDATES_SQL).doesNotContain("FOR UPDATE")
                .contains("expires_at <= now()");
        assertThat(OrderPayments.LOCK_EXPIRED_SQL).contains("FOR UPDATE OF payment SKIP LOCKED")
                .contains("payment.id = :paymentId")
                .contains("expires_at <= now()").contains("'PENDING', 'REJECTED', 'CANCELLED'");
        assertThat(OrderPayments.EXPIRE_PAYMENT_SQL).contains("'EXPIRED'")
                .contains("code IN ('PENDING', 'REJECTED', 'CANCELLED')");
        assertThat(jdbc.statements().get(0).param("limit")).isEqualTo(50);
        assertThat(jdbc.statements().get(1).param("paymentId")).isEqualTo(PAYMENT_ID);
        assertThat(jdbc.statements().getLast().param("statusCode")).isEqualTo("EXPIRED");

        // Tanda siguiente: paginación por (expires_at, id) tras el último candidato.
        payments.findExpired("WOMPI", first, 50);
        Statement next = jdbc.statements().getLast();
        assertThat(next.sql()).isEqualTo(OrderPayments.EXPIRED_CANDIDATES_AFTER_SQL)
                .contains("(payment.expires_at, payment.id) > (:afterExpiresAt, :afterPaymentId)")
                .contains("ORDER BY payment.expires_at, payment.id");
        assertThat(next.param("afterExpiresAt")).isEqualTo(expiresAt);
        assertThat(next.param("afterPaymentId")).isEqualTo(PAYMENT_ID);
        assertThat(OrderPayments.EXPIRED_CANDIDATES_SQL).doesNotContain(":afterPaymentId");
    }

    @Test
    void reconciliationTakesOpenUnexpiredPaymentsOfTheEnvironment() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement -> List.of(RecordingJdbcClient.row(
                Map.of("provider_reference", REFERENCE))));

        assertThat(new OrderPayments(jdbc.client()).lockReconciliationCandidates("WOMPI", "SANDBOX", 120, 50))
                .containsExactly(new OrderPayments.ReconciliationCandidate(REFERENCE, null, null));
        Statement statement = jdbc.statements().getFirst();
        // Omite los vencidos: el vencimiento los consulta en Wompi justo antes de cerrarlos.
        assertThat(statement.sql()).contains("SKIP LOCKED").contains("expires_at > now()")
                .contains("payment.environment = :environment");
        assertThat(statement.param("minAgeSeconds")).isEqualTo(120L);
    }
}
