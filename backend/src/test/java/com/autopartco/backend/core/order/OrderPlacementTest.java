package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.autopartco.backend.core.order.RecordingJdbcClient.Statement;

class OrderPlacementTest {
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID SALE_A = UUID.randomUUID();
    private static final UUID SALE_B = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID INVENTORY_A1 = UUID.randomUUID();
    private static final UUID INVENTORY_A2 = UUID.randomUUID();
    private static final UUID INVENTORY_B1 = UUID.randomUUID();
    private static final UUID PRODUCT_A1 = UUID.randomUUID();
    private static final UUID PRODUCT_A2 = UUID.randomUUID();
    private static final UUID PRODUCT_B1 = UUID.randomUUID();

    @Test
    void placeApprovedInsertsInOrderWithStatusCodes() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(returningIds());
        OrderPlacement placement = new OrderPlacement(jdbc.client());

        UUID result = placement.placeApproved(order());

        assertThat(result).isEqualTo(ORDER_ID);
        List<Statement> statements = jdbc.statements();
        assertThat(statements).extracting(Statement::sql).containsExactly(
                OrderPlacement.INSERT_ORDER_SQL,
                OrderPlacement.INSERT_ORDER_ADDRESS_SQL,
                OrderPlacement.INSERT_SALE_SQL,
                OrderPlacement.INSERT_ORDER_DETAIL_SQL,
                OrderPlacement.INSERT_ORDER_DETAIL_SQL,
                OrderPlacement.INSERT_SALE_SQL,
                OrderPlacement.INSERT_ORDER_DETAIL_SQL,
                OrderPlacement.INSERT_PAYMENT_SQL,
                OrderPlacement.INSERT_PAYMENT_EVENT_SQL);

        Statement insertOrder = statements.get(0);
        assertThat(insertOrder.param("personId")).isEqualTo(PERSON_ID);
        assertThat(insertOrder.param("stateCode")).isEqualTo("CONFIRMED");

        Statement address = statements.get(1);
        assertThat(address.param("orderId")).isEqualTo(ORDER_ID);
        assertThat(address.param("addressLine")).isEqualTo("Calle 10 # 20-30");
        assertThat(address.params()).containsEntry("addressComplement", null).containsEntry("latitude", null);

        Statement saleA = statements.get(2);
        assertThat(saleA.param("orderId")).isEqualTo(ORDER_ID);
        assertThat(saleA.param("branchId")).isEqualTo(BRANCH_A);
        assertThat(saleA.param("stateCode")).isEqualTo("PENDING");
        assertThat(saleA.param("total")).isEqualTo(new BigDecimal("180000.00"));
        assertThat(statements.get(5).param("stateCode")).isEqualTo("PENDING");

        Statement detailA1 = statements.get(3);
        assertThat(detailA1.param("orderId")).isEqualTo(ORDER_ID);
        assertThat(detailA1.param("saleId")).isEqualTo(SALE_A);
        assertThat(detailA1.param("inventoryId")).isEqualTo(INVENTORY_A1);
        assertThat(OrderPlacement.INSERT_ORDER_DETAIL_SQL).contains("product_id", ":productId");
        assertThat(detailA1.param("productId")).isEqualTo(PRODUCT_A1);
        assertThat(statements.get(4).param("productId")).isEqualTo(PRODUCT_A2);
        assertThat(statements.get(6).param("productId")).isEqualTo(PRODUCT_B1);
        assertThat(detailA1.param("units")).isEqualTo(2);
        assertThat(detailA1.param("unitPrice")).isEqualTo(new BigDecimal("80000.00"));
        assertThat(statements.get(6).param("saleId")).isEqualTo(SALE_B);

        Statement payment = statements.get(7);
        assertThat(payment.param("orderId")).isEqualTo(ORDER_ID);
        assertThat(payment.param("statusCode")).isEqualTo("APPROVED");
        assertThat(payment.param("methodCode")).isEqualTo("SIMULATED_CARD");
        assertThat(payment.param("provider")).isEqualTo("SIMULATED");
        assertThat(payment.param("currency")).isEqualTo("COP");
        assertThat(payment.param("reference")).isEqualTo("0b6f7a52-2f0e-4a3c-9d2e-6a1f8f6a4c11");
        assertThat(payment.sql()).contains("now()");

        Statement event = statements.get(8);
        assertThat(event.param("paymentId")).isEqualTo(PAYMENT_ID);
        assertThat(event.param("statusCode")).isEqualTo("APPROVED");
        assertThat(event.param("message")).isEqualTo("Pago simulado aprobado");
    }

    @Test
    void paymentAmountIsTheSumOfSaleTotals() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(returningIds());

        new OrderPlacement(jdbc.client()).placeApproved(order());

        Statement payment = jdbc.statements().stream()
                .filter(statement -> statement.sql().equals(OrderPlacement.INSERT_PAYMENT_SQL))
                .findFirst()
                .orElseThrow();
        // 180000.00 + 50000.50 (incluye envío 0.00)
        assertThat(payment.param("amount")).isEqualTo(new BigDecimal("230000.50"));
    }

    @Test
    void duplicatedReferencePropagatesDuplicateKeyException() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(statement -> {
            if (statement.sql().equals(OrderPlacement.INSERT_PAYMENT_SQL)) {
                throw new DuplicateKeyException("uq_payment_provider_reference");
            }
            return returningIds().apply(statement);
        });

        assertThatThrownBy(() -> new OrderPlacement(jdbc.client()).placeApproved(order()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void activePaymentMethodChecksCodeActiveAndNotDeleted() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(statement -> Boolean.TRUE);
        OrderPlacement placement = new OrderPlacement(jdbc.client());

        assertThat(placement.isActivePaymentMethod("SIMULATED_CARD")).isTrue();
        assertThat(placement.isActivePaymentMethod(" ")).isFalse();
        assertThat(jdbc.statements()).hasSize(1);
        Statement statement = jdbc.statements().getFirst();
        assertThat(statement.sql()).contains("active = true").contains("deleted_at IS NULL");
        assertThat(statement.param("code")).isEqualTo("SIMULATED_CARD");
    }

    @Test
    void inactivePaymentMethodIsNotActive() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(statement -> Boolean.FALSE);

        assertThat(new OrderPlacement(jdbc.client()).isActivePaymentMethod("SIMULATED_CASH")).isFalse();
    }

    @Test
    void findsOwnerOfSimulatedPaymentReference() {
        UUID orderId = UUID.randomUUID();
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement -> List.of(RecordingJdbcClient.row(
                Map.of("order_id", orderId, "person_id", PERSON_ID))));
        OrderPlacement placement = new OrderPlacement(jdbc.client());

        assertThat(placement.findByPaymentReference("ref-1")).contains(new PaymentReferenceOwner(orderId, PERSON_ID));
        Statement statement = jdbc.statements().getFirst();
        assertThat(statement.param("provider")).isEqualTo("SIMULATED");
        assertThat(statement.param("reference")).isEqualTo("ref-1");
    }

    @Test
    void unknownPaymentReferenceHasNoOwner() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient();

        assertThat(new OrderPlacement(jdbc.client()).findByPaymentReference("ref-2")).isEmpty();
    }

    private static java.util.function.Function<Statement, Object> returningIds() {
        Map<String, Integer> salesInserted = new HashMap<>();
        return statement -> {
            if (statement.sql().equals(OrderPlacement.INSERT_ORDER_SQL)) {
                return ORDER_ID;
            }
            if (statement.sql().equals(OrderPlacement.INSERT_SALE_SQL)) {
                return salesInserted.merge("sales", 1, Integer::sum) == 1 ? SALE_A : SALE_B;
            }
            if (statement.sql().equals(OrderPlacement.INSERT_PAYMENT_SQL)) {
                return PAYMENT_ID;
            }
            throw new IllegalStateException("Unexpected scalar query: " + statement.sql());
        };
    }

    private static NewOrder order() {
        return new NewOrder(
                PERSON_ID,
                new NewOrder.DeliveryAddress("Calle 10 # 20-30", null, "Bogotá", "Cundinamarca", "Colombia",
                        "110111", null, null),
                "SIMULATED_CARD",
                "0b6f7a52-2f0e-4a3c-9d2e-6a1f8f6a4c11",
                List.of(
                        new NewOrder.NewSale(BRANCH_A, new BigDecimal("180000.00"), new BigDecimal("0.00"),
                                new BigDecimal("180000.00"), List.of(
                                        new NewOrder.NewLine(INVENTORY_A1, PRODUCT_A1, 2, new BigDecimal("80000.00")),
                                        new NewOrder.NewLine(INVENTORY_A2, PRODUCT_A2, 1, new BigDecimal("20000.00")))),
                        new NewOrder.NewSale(BRANCH_B, new BigDecimal("50000.50"), new BigDecimal("0.00"),
                                new BigDecimal("50000.50"), List.of(
                                        new NewOrder.NewLine(INVENTORY_B1, PRODUCT_B1, 1, new BigDecimal("50000.50"))))));
    }
}
