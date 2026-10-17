package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.order.RecordingJdbcClient.Statement;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

class BuyerOrderQueriesTest {
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID OTHER_PERSON_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID SHIPMENT_ID = UUID.randomUUID();
    private static final UUID SALE_A = UUID.randomUUID();
    private static final UUID SALE_B = UUID.randomUUID();
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID PRODUCT_1 = UUID.randomUUID();
    private static final UUID PRODUCT_2 = UUID.randomUUID();
    private static final UUID PRODUCT_3 = UUID.randomUUID();
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 14, 15, 30);

    @Test
    void receiptBuildsTheContractShapeWithTotals() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(BuyerOrderQueriesTest::ownOrderRows);

        BuyerOrderResponse order = new BuyerOrderQueries(jdbc.client()).receipt(PERSON_ID, ORDER_ID);

        assertThat(order.orderId()).isEqualTo(ORDER_ID);
        assertThat(order.orderNumber()).isEqualTo(1042L);
        assertThat(order.status()).isEqualTo("CONFIRMED");
        assertThat(order.createdAt()).isEqualTo(CREATED_AT);
        assertThat(order.currency()).isEqualTo("COP");
        assertThat(order.subtotal()).isEqualTo(new BigDecimal("230000.00"));
        assertThat(order.shippingTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(order.total()).isEqualTo(new BigDecimal("230000.00"));

        assertThat(order.payment()).isEqualTo(new BuyerOrderResponse.Payment(
                "APPROVED", "SIMULATED_CARD", "Tarjeta simulada", "ref-1", new BigDecimal("230000.00"), CREATED_AT));
        assertThat(order.deliveryAddress()).isEqualTo(new BuyerOrderResponse.DeliveryAddress(
                "Calle 10 # 20-30", "Apto 401", "Bogotá", "Cundinamarca", "Colombia", "110111"));

        assertThat(order.sales()).extracting(BuyerOrderResponse.Sale::saleId).containsExactly(SALE_A, SALE_B);
        BuyerOrderResponse.Sale first = order.sales().getFirst();
        assertThat(first.storeName()).isEqualTo("Repuestos El Cruce");
        assertThat(first.branchName()).isEqualTo("Sede Norte");
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.total()).isEqualTo(new BigDecimal("180000.00"));
        assertThat(first.items()).containsExactly(
                new BuyerOrderResponse.Item(PRODUCT_1, "Bujía", "BJ-1", 1, new BigDecimal("20000.00"),
                        new BigDecimal("20000.00")),
                new BuyerOrderResponse.Item(PRODUCT_2, "Pastillas de freno", "BP-1234", 2,
                        new BigDecimal("80000.00"), new BigDecimal("160000.00")));
        assertThat(order.sales().get(1).items()).extracting(BuyerOrderResponse.Item::productId)
                .containsExactly(PRODUCT_3);

        Statement header = jdbc.statements().getFirst();
        assertThat(header.sql())
                .contains("customer_order.person_id = :personId")
                .contains("customer_order.deleted_at IS NULL")
                .contains("ORDER BY payment_record.created_at DESC")
                .contains("LIMIT 1");
        assertThat(header.param("personId")).isEqualTo(PERSON_ID);
        assertThat(header.param("orderId")).isEqualTo(ORDER_ID);
        assertThat(jdbc.statements().get(1).sql()).contains("store.business_name AS store_name")
                .contains("ORDER BY store.business_name, branch.name");
        assertThat(jdbc.statements().get(2).sql()).contains("ORDER BY product.name");
    }

    @Test
    void receiptOfAnotherPersonIsOrderNotFound() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement ->
                PERSON_ID.equals(statement.param("personId")) ? ownOrderRows(statement) : List.of());

        assertThatThrownBy(() -> new BuyerOrderQueries(jdbc.client()).receipt(OTHER_PERSON_ID, ORDER_ID))
                .isInstanceOfSatisfying(NotFoundException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("ORDER_NOT_FOUND");
                    assertThat(error.getMessage()).isEqualTo("No se encontró el pedido.");
                });
        assertThat(jdbc.statements()).hasSize(1);
        assertThat(jdbc.statements().getFirst().param("personId")).isEqualTo(OTHER_PERSON_ID);
    }

    @Test
    void receiptHasNoShipmentsAndSkipsShipmentQueries() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(BuyerOrderQueriesTest::ownOrderRows);

        BuyerOrderResponse order = new BuyerOrderQueries(jdbc.client()).receipt(PERSON_ID, ORDER_ID);

        assertThat(order.sales()).extracting(BuyerOrderResponse.Sale::shipment).containsOnlyNulls();
        assertThat(jdbc.statements()).hasSize(3);
    }

    @Test
    void detailAddsShipmentWithEventsOrderedAndNullWhenThereIsNone() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(BuyerOrderQueriesTest::ownOrderRows);

        BuyerOrderResponse order = new BuyerOrderQueries(jdbc.client()).detail(PERSON_ID, ORDER_ID);

        BuyerOrderResponse.Shipment shipment = order.sales().getFirst().shipment();
        assertThat(shipment.carrier()).isEqualTo("SIMULATED");
        assertThat(shipment.trackingNumber()).isEqualTo("AP-000123");
        assertThat(shipment.status()).isEqualTo("IN_TRANSIT");
        assertThat(shipment.shippedAt()).isEqualTo(CREATED_AT);
        assertThat(shipment.deliveredAt()).isNull();
        assertThat(shipment.events()).containsExactly(
                new BuyerOrderResponse.Shipment.Event("DISPATCHED", null, CREATED_AT));
        assertThat(order.sales().get(1).shipment()).isNull();
        assertThat(jdbc.statements()).anySatisfy(statement -> {
            assertThat(statement.sql()).isEqualTo(BuyerOrderQueries.SHIPMENT_EVENTS_SQL)
                    .contains("ORDER BY shipment_event.event_at");
            assertThat(statement.param("orderId")).isEqualTo(ORDER_ID);
        });
    }

    @Test
    void detailOfAnotherPersonIsOrderNotFound() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onRows(statement -> List.of());

        assertThatThrownBy(() -> new BuyerOrderQueries(jdbc.client()).detail(OTHER_PERSON_ID, ORDER_ID))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        assertThat(jdbc.statements()).hasSize(1);
    }

    @Test
    void listBuildsSummariesFilteredByPersonNewestFirst() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient()
                .onScalar(statement -> 11L)
                .onRows(BuyerOrderQueriesTest::pageRows);

        PageResponse<BuyerOrderSummary> page = new BuyerOrderQueries(jdbc.client()).list(PERSON_ID, 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(11L);
        assertThat(page.totalPages()).isEqualTo(2);
        BuyerOrderSummary summary = page.items().getFirst();
        assertThat(summary.orderNumber()).isEqualTo(1042L);
        assertThat(summary.currency()).isEqualTo("COP");
        assertThat(summary.total()).isEqualTo(new BigDecimal("230000.00"));
        assertThat(summary.itemCount()).isEqualTo(3);
        assertThat(summary.sales()).containsExactly(
                new BuyerOrderSummary.SaleRef(SALE_A, STORE_A, "Repuestos El Cruce", "SHIPPED"));

        assertThat(jdbc.statements()).allSatisfy(statement -> assertThat(statement.sql()).doesNotContain("SELECT *"));
        Statement count = jdbc.statements().getFirst();
        assertThat(count.sql()).contains("person_id = :personId").contains("deleted_at IS NULL");
        assertThat(count.param("personId")).isEqualTo(PERSON_ID);
        Statement pageQuery = jdbc.statements().get(1);
        assertThat(pageQuery.sql()).contains("customer_order.person_id = :personId")
                .contains("ORDER BY customer_order.created_at DESC");
        assertThat(pageQuery.param("limit")).isEqualTo(10);
        assertThat(pageQuery.param("offset")).isEqualTo(10L);
        assertThat(jdbc.statements().get(2).param("orderIds")).isEqualTo(List.of(ORDER_ID));
    }

    @Test
    void listWithNoOrdersSkipsThePageQuery() {
        RecordingJdbcClient jdbc = new RecordingJdbcClient().onScalar(statement -> 0L);

        PageResponse<BuyerOrderSummary> page = new BuyerOrderQueries(jdbc.client()).list(PERSON_ID, 1, 10);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(jdbc.statements()).hasSize(1);
    }

    @Test
    void listRejectsInvalidPagination() {
        BuyerOrderQueries queries = new BuyerOrderQueries(new RecordingJdbcClient().client());

        assertThatThrownBy(() -> queries.list(PERSON_ID, 1, 51)).isInstanceOfSatisfying(BadRequestException.class,
                error -> assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR"));
        assertThatThrownBy(() -> queries.list(PERSON_ID, 1, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> queries.list(PERSON_ID, 0, 10)).isInstanceOf(BadRequestException.class);
    }

    private static List<ResultSet> pageRows(Statement statement) {
        if (statement.sql().equals(BuyerOrderQueries.PAGE_SQL)) {
            Map<String, Object> order = new HashMap<>();
            order.put("order_id", ORDER_ID);
            order.put("order_number", 1042L);
            order.put("order_status", "CONFIRMED");
            order.put("created_at", Timestamp.valueOf(CREATED_AT));
            order.put("currency", null);
            order.put("total", new BigDecimal("230000"));
            order.put("item_count", 3);
            return List.of(RecordingJdbcClient.row(order));
        }
        if (statement.sql().equals(BuyerOrderQueries.PAGE_SALES_SQL)) {
            return List.of(RecordingJdbcClient.row(Map.of(
                    "order_id", ORDER_ID,
                    "sale_id", SALE_A,
                    "store_id", STORE_A,
                    "store_name", "Repuestos El Cruce",
                    "sale_status", "SHIPPED")));
        }
        throw new IllegalStateException("Unexpected query: " + statement.sql());
    }

    private static List<ResultSet> ownOrderRows(Statement statement) {
        if (statement.sql().equals(BuyerOrderQueries.SHIPMENTS_SQL)) {
            Map<String, Object> shipment = new HashMap<>();
            shipment.put("shipment_id", SHIPMENT_ID);
            shipment.put("sale_id", SALE_A);
            shipment.put("carrier", "SIMULATED");
            shipment.put("tracking_number", "AP-000123");
            shipment.put("shipment_status", "IN_TRANSIT");
            shipment.put("shipped_at", Timestamp.valueOf(CREATED_AT));
            shipment.put("delivered_at", null);
            return List.of(RecordingJdbcClient.row(shipment));
        }
        if (statement.sql().equals(BuyerOrderQueries.SHIPMENT_EVENTS_SQL)) {
            Map<String, Object> event = new HashMap<>();
            event.put("shipment_id", SHIPMENT_ID);
            event.put("event_status", "DISPATCHED");
            event.put("notes", null);
            event.put("event_at", Timestamp.valueOf(CREATED_AT));
            return List.of(RecordingJdbcClient.row(event));
        }
        if (statement.sql().equals(BuyerOrderQueries.ORDER_SQL)) {
            if (!PERSON_ID.equals(statement.param("personId"))) {
                return List.of();
            }
            Map<String, Object> order = new HashMap<>();
            order.put("order_id", ORDER_ID);
            order.put("order_number", 1042L);
            order.put("order_status", "CONFIRMED");
            order.put("created_at", Timestamp.valueOf(CREATED_AT));
            order.put("payment_status", "APPROVED");
            order.put("method_code", "SIMULATED_CARD");
            order.put("method_name", "Tarjeta simulada");
            order.put("provider_reference", "ref-1");
            order.put("payment_amount", new BigDecimal("230000"));
            order.put("currency", "COP");
            order.put("paid_at", Timestamp.valueOf(CREATED_AT));
            order.put("address_line", "Calle 10 # 20-30");
            order.put("address_complement", "Apto 401");
            order.put("city", "Bogotá");
            order.put("department", "Cundinamarca");
            order.put("country", "Colombia");
            order.put("postal_code", "110111");
            return List.of(RecordingJdbcClient.row(order));
        }
        if (statement.sql().equals(BuyerOrderQueries.SALES_SQL)) {
            return List.of(
                    sale(SALE_A, STORE_A, "Repuestos El Cruce", BRANCH_A, "Sede Norte", "180000.00"),
                    sale(SALE_B, STORE_B, "Taller Sur", BRANCH_B, "Principal", "50000.00"));
        }
        if (statement.sql().equals(BuyerOrderQueries.ITEMS_SQL)) {
            return List.of(
                    item(SALE_A, PRODUCT_1, "Bujía", "BJ-1", 1, "20000.00"),
                    item(SALE_B, PRODUCT_3, "Filtro", "FL-9", 1, "50000.00"),
                    item(SALE_A, PRODUCT_2, "Pastillas de freno", "BP-1234", 2, "80000.00"));
        }
        throw new IllegalStateException("Unexpected query: " + statement.sql());
    }

    private static ResultSet sale(UUID saleId, UUID storeId, String storeName, UUID branchId, String branchName,
                                  String total) {
        return RecordingJdbcClient.row(Map.of(
                "sale_id", saleId,
                "store_id", storeId,
                "store_name", storeName,
                "branch_id", branchId,
                "branch_name", branchName,
                "sale_status", "PENDING",
                "subtotal", new BigDecimal(total),
                "shipping_cost", new BigDecimal("0.00"),
                "total", new BigDecimal(total)));
    }

    private static ResultSet item(UUID saleId, UUID productId, String name, String partNumber, int units,
                                  String unitPrice) {
        return RecordingJdbcClient.row(Map.of(
                "sale_id", saleId,
                "product_id", productId,
                "name", name,
                "part_number", partNumber,
                "units", units,
                "unit_price", new BigDecimal(unitPrice)));
    }
}
