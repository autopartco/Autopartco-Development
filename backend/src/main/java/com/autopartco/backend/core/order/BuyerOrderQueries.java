package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Lecturas del pedido desde el punto de vista del comprador, con {@link JdbcClient}. Siempre filtra por la persona
 * dueña del pedido; un pedido ajeno se trata igual que uno inexistente.
 */
@Service
@AllArgsConstructor
public class BuyerOrderQueries {
    static final String DEFAULT_CURRENCY = "COP";

    static final String ORDER_SQL = """
            SELECT customer_order.id AS order_id,
                   customer_order.order_number,
                   order_state.code AS order_status,
                   customer_order.created_at,
                   payment.payment_status,
                   payment.method_code,
                   payment.method_name,
                   payment.provider_reference,
                   payment.amount AS payment_amount,
                   payment.currency,
                   payment.paid_at,
                   delivery.address_line,
                   delivery.address_complement,
                   delivery.city,
                   delivery.department,
                   delivery.country,
                   delivery.postal_code
            FROM orders customer_order
            JOIN states order_state ON order_state.id = customer_order.state_id
            LEFT JOIN order_addresses delivery ON delivery.order_id = customer_order.id
            LEFT JOIN LATERAL (
                SELECT status.code AS payment_status,
                       method.code AS method_code,
                       method.name AS method_name,
                       payment_record.provider_reference,
                       payment_record.amount,
                       payment_record.currency,
                       payment_record.paid_at
                FROM payments payment_record
                JOIN payment_statuses status ON status.id = payment_record.payment_status_id
                JOIN payment_methods method ON method.id = payment_record.payment_method_id
                WHERE payment_record.order_id = customer_order.id
                ORDER BY payment_record.created_at DESC
                LIMIT 1
            ) payment ON true
            WHERE customer_order.id = :orderId
              AND customer_order.person_id = :personId
              AND customer_order.deleted_at IS NULL
            """;

    static final String SALES_SQL = """
            SELECT sale.id AS sale_id,
                   branch.store_id,
                   store.business_name AS store_name,
                   branch.id AS branch_id,
                   branch.name AS branch_name,
                   sale_state.code AS sale_status,
                   sale.subtotal,
                   sale.shipping_cost,
                   sale.total
            FROM sales sale
            JOIN branches branch ON branch.id = sale.branch_id
            JOIN store ON store.id = branch.store_id
            JOIN states sale_state ON sale_state.id = sale.state_id
            WHERE sale.order_id = :orderId
              AND sale.deleted_at IS NULL
            ORDER BY store.business_name, branch.name, sale.id
            """;

    static final String ITEMS_SQL = """
            SELECT detail.sale_id,
                   product.id AS product_id,
                   product.name,
                   product.part_number,
                   detail.units,
                   detail.unit_price
            FROM order_details detail
            JOIN inventory ON inventory.id = detail.inventory_id
            JOIN products product ON product.id = inventory.product_id
            WHERE detail.order_id = :orderId
              AND detail.deleted_at IS NULL
            ORDER BY product.name, product.id
            """;

    static final int MAX_PAGE_SIZE = 50;

    static final String COUNT_SQL = """
            SELECT count(*)
            FROM orders customer_order
            WHERE customer_order.person_id = :personId
              AND customer_order.deleted_at IS NULL
            """;

    static final String PAGE_SQL = """
            SELECT customer_order.id AS order_id,
                   customer_order.order_number,
                   order_state.code AS order_status,
                   customer_order.created_at,
                   (SELECT payment_record.currency
                    FROM payments payment_record
                    WHERE payment_record.order_id = customer_order.id
                    ORDER BY payment_record.created_at DESC
                    LIMIT 1) AS currency,
                   COALESCE((SELECT sum(sale.total)
                             FROM sales sale
                             WHERE sale.order_id = customer_order.id
                               AND sale.deleted_at IS NULL), 0) AS total,
                   COALESCE((SELECT sum(detail.units)
                             FROM order_details detail
                             WHERE detail.order_id = customer_order.id
                               AND detail.deleted_at IS NULL), 0) AS item_count
            FROM orders customer_order
            JOIN states order_state ON order_state.id = customer_order.state_id
            WHERE customer_order.person_id = :personId
              AND customer_order.deleted_at IS NULL
            ORDER BY customer_order.created_at DESC, customer_order.id DESC
            LIMIT :limit OFFSET :offset
            """;

    static final String PAGE_SALES_SQL = """
            SELECT sale.order_id,
                   sale.id AS sale_id,
                   branch.store_id,
                   store.business_name AS store_name,
                   sale_state.code AS sale_status
            FROM sales sale
            JOIN branches branch ON branch.id = sale.branch_id
            JOIN store ON store.id = branch.store_id
            JOIN states sale_state ON sale_state.id = sale.state_id
            WHERE sale.order_id IN (:orderIds)
              AND sale.deleted_at IS NULL
            ORDER BY store.business_name, branch.name, sale.id
            """;

    static final String SHIPMENTS_SQL = """
            SELECT shipment.id AS shipment_id,
                   shipment.sale_id,
                   shipment.carrier,
                   shipment.tracking_number,
                   shipment_state.code AS shipment_status,
                   shipment.shipped_at,
                   shipment.delivered_at
            FROM shipments shipment
            JOIN sales sale ON sale.id = shipment.sale_id
            JOIN shipment_statuses shipment_state ON shipment_state.id = shipment.shipment_status_id
            WHERE sale.order_id = :orderId
              AND sale.deleted_at IS NULL
            """;

    static final String SHIPMENT_EVENTS_SQL = """
            SELECT shipment_event.shipment_id,
                   event_state.code AS event_status,
                   shipment_event.notes,
                   shipment_event.event_at
            FROM shipment_events shipment_event
            JOIN shipments shipment ON shipment.id = shipment_event.shipment_id
            JOIN sales sale ON sale.id = shipment.sale_id
            JOIN shipment_statuses event_state ON event_state.id = shipment_event.shipment_status_id
            WHERE sale.order_id = :orderId
              AND sale.deleted_at IS NULL
            ORDER BY shipment_event.event_at, shipment_event.id
            """;

    private final JdbcClient jdbcClient;

    /**
     * Recibo del checkout: pedido de esa persona con la forma del contrato y sin envíos ({@code shipment} nulo); si no
     * existe o es ajeno, {@code ORDER_NOT_FOUND}.
     */
    @Transactional(readOnly = true)
    public BuyerOrderResponse receipt(UUID personId, UUID orderId) {
        return load(personId, orderId, false);
    }

    /** Detalle de "Mis pedidos": el recibo más el envío (con sus eventos) de cada venta que lo tenga. */
    @Transactional(readOnly = true)
    public BuyerOrderResponse detail(UUID personId, UUID orderId) {
        return load(personId, orderId, true);
    }

    /** Pedidos de la persona, del más reciente al más antiguo. {@code page} desde 1; {@code size} entre 1 y 50. */
    @Transactional(readOnly = true)
    public PageResponse<BuyerOrderSummary> list(UUID personId, int page, int size) {
        if (page < 1) {
            throw new BadRequestException("VALIDATION_ERROR", "La página debe ser mayor o igual a 1.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("VALIDATION_ERROR",
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        long total = jdbcClient.sql(COUNT_SQL).param("personId", personId).query(Long.class).single();
        if (total == 0) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        List<SummaryRow> rows = jdbcClient.sql(PAGE_SQL)
                .param("personId", personId)
                .param("limit", size)
                .param("offset", (long) (page - 1) * size)
                .query(BuyerOrderQueries::mapSummary)
                .list();
        Map<UUID, List<BuyerOrderSummary.SaleRef>> salesByOrder = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            List<UUID> orderIds = rows.stream().map(SummaryRow::orderId).toList();
            jdbcClient.sql(PAGE_SALES_SQL)
                    .param("orderIds", orderIds)
                    .query(BuyerOrderQueries::mapSaleRef)
                    .list()
                    .forEach(ref -> salesByOrder.computeIfAbsent(ref.orderId(), ignored -> new ArrayList<>())
                            .add(ref.ref()));
        }
        List<BuyerOrderSummary> items = rows.stream()
                .map(row -> new BuyerOrderSummary(
                        row.orderId(),
                        row.orderNumber(),
                        row.status(),
                        row.createdAt(),
                        row.currency() == null ? DEFAULT_CURRENCY : row.currency(),
                        money(row.total()),
                        row.itemCount(),
                        List.copyOf(salesByOrder.getOrDefault(row.orderId(), List.of()))))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    private BuyerOrderResponse load(UUID personId, UUID orderId, boolean withShipments) {
        OrderRow order = jdbcClient.sql(ORDER_SQL)
                .param("orderId", orderId)
                .param("personId", personId)
                .query(BuyerOrderQueries::mapOrder)
                .list()
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido."));

        List<SaleRow> saleRows = jdbcClient.sql(SALES_SQL)
                .param("orderId", orderId)
                .query(BuyerOrderQueries::mapSale)
                .list();
        List<ItemRow> itemRows = jdbcClient.sql(ITEMS_SQL)
                .param("orderId", orderId)
                .query(BuyerOrderQueries::mapItem)
                .list();

        Map<UUID, BuyerOrderResponse.Shipment> shipmentsBySale = withShipments
                ? loadShipments(orderId)
                : Map.of();

        Map<UUID, List<BuyerOrderResponse.Item>> itemsBySale = new LinkedHashMap<>();
        for (ItemRow item : itemRows) {
            itemsBySale.computeIfAbsent(item.saleId(), ignored -> new ArrayList<>()).add(item.item());
        }

        BigDecimal subtotal = money(BigDecimal.ZERO);
        BigDecimal shippingTotal = money(BigDecimal.ZERO);
        List<BuyerOrderResponse.Sale> sales = new ArrayList<>();
        for (SaleRow sale : saleRows) {
            subtotal = subtotal.add(sale.subtotal());
            shippingTotal = shippingTotal.add(sale.shippingCost());
            sales.add(new BuyerOrderResponse.Sale(
                    sale.saleId(),
                    sale.storeId(),
                    sale.storeName(),
                    sale.branchId(),
                    sale.branchName(),
                    sale.status(),
                    money(sale.subtotal()),
                    money(sale.shippingCost()),
                    money(sale.total()),
                    List.copyOf(itemsBySale.getOrDefault(sale.saleId(), List.of())),
                    shipmentsBySale.get(sale.saleId())
            ));
        }
        subtotal = money(subtotal);
        shippingTotal = money(shippingTotal);

        return new BuyerOrderResponse(
                order.orderId(),
                order.orderNumber(),
                order.status(),
                order.createdAt(),
                order.currency() == null ? DEFAULT_CURRENCY : order.currency(),
                subtotal,
                shippingTotal,
                money(subtotal.add(shippingTotal)),
                order.payment(),
                order.address(),
                List.copyOf(sales)
        );
    }

    private Map<UUID, BuyerOrderResponse.Shipment> loadShipments(UUID orderId) {
        List<ShipmentRow> shipments = jdbcClient.sql(SHIPMENTS_SQL)
                .param("orderId", orderId)
                .query(BuyerOrderQueries::mapShipment)
                .list();
        if (shipments.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<BuyerOrderResponse.Shipment.Event>> eventsByShipment = new LinkedHashMap<>();
        jdbcClient.sql(SHIPMENT_EVENTS_SQL)
                .param("orderId", orderId)
                .query(BuyerOrderQueries::mapEvent)
                .list()
                .forEach(event -> eventsByShipment.computeIfAbsent(event.shipmentId(), ignored -> new ArrayList<>())
                        .add(event.event()));
        Map<UUID, BuyerOrderResponse.Shipment> bySale = new LinkedHashMap<>();
        for (ShipmentRow row : shipments) {
            bySale.put(row.saleId(), new BuyerOrderResponse.Shipment(
                    row.carrier(),
                    row.trackingNumber(),
                    row.status(),
                    row.shippedAt(),
                    row.deliveredAt(),
                    List.copyOf(eventsByShipment.getOrDefault(row.shipmentId(), List.of()))));
        }
        return bySale;
    }

    private static SummaryRow mapSummary(ResultSet result, int row) throws SQLException {
        return new SummaryRow(
                result.getObject("order_id", UUID.class),
                result.getLong("order_number"),
                result.getString("order_status"),
                localDateTime(result, "created_at"),
                result.getString("currency"),
                result.getBigDecimal("total"),
                result.getInt("item_count"));
    }

    private static SaleRefRow mapSaleRef(ResultSet result, int row) throws SQLException {
        return new SaleRefRow(
                result.getObject("order_id", UUID.class),
                new BuyerOrderSummary.SaleRef(
                        result.getObject("sale_id", UUID.class),
                        result.getObject("store_id", UUID.class),
                        result.getString("store_name"),
                        result.getString("sale_status")));
    }

    private static ShipmentRow mapShipment(ResultSet result, int row) throws SQLException {
        return new ShipmentRow(
                result.getObject("shipment_id", UUID.class),
                result.getObject("sale_id", UUID.class),
                result.getString("carrier"),
                result.getString("tracking_number"),
                result.getString("shipment_status"),
                localDateTime(result, "shipped_at"),
                localDateTime(result, "delivered_at"));
    }

    private static EventRow mapEvent(ResultSet result, int row) throws SQLException {
        return new EventRow(
                result.getObject("shipment_id", UUID.class),
                new BuyerOrderResponse.Shipment.Event(
                        result.getString("event_status"),
                        result.getString("notes"),
                        localDateTime(result, "event_at")));
    }

    private static OrderRow mapOrder(ResultSet result, int row) throws SQLException {
        BuyerOrderResponse.Payment payment = result.getString("payment_status") == null
                ? null
                : new BuyerOrderResponse.Payment(
                        result.getString("payment_status"),
                        result.getString("method_code"),
                        result.getString("method_name"),
                        result.getString("provider_reference"),
                        money(result.getBigDecimal("payment_amount")),
                        localDateTime(result, "paid_at"));
        BuyerOrderResponse.DeliveryAddress address = result.getString("address_line") == null
                ? null
                : new BuyerOrderResponse.DeliveryAddress(
                        result.getString("address_line"),
                        result.getString("address_complement"),
                        result.getString("city"),
                        result.getString("department"),
                        result.getString("country"),
                        result.getString("postal_code"));
        return new OrderRow(
                result.getObject("order_id", UUID.class),
                result.getLong("order_number"),
                result.getString("order_status"),
                localDateTime(result, "created_at"),
                result.getString("currency"),
                payment,
                address);
    }

    private static SaleRow mapSale(ResultSet result, int row) throws SQLException {
        return new SaleRow(
                result.getObject("sale_id", UUID.class),
                result.getObject("store_id", UUID.class),
                result.getString("store_name"),
                result.getObject("branch_id", UUID.class),
                result.getString("branch_name"),
                result.getString("sale_status"),
                result.getBigDecimal("subtotal"),
                result.getBigDecimal("shipping_cost"),
                result.getBigDecimal("total"));
    }

    private static ItemRow mapItem(ResultSet result, int row) throws SQLException {
        int units = result.getInt("units");
        BigDecimal unitPrice = money(result.getBigDecimal("unit_price"));
        return new ItemRow(
                result.getObject("sale_id", UUID.class),
                new BuyerOrderResponse.Item(
                        result.getObject("product_id", UUID.class),
                        result.getString("name"),
                        result.getString("part_number"),
                        units,
                        unitPrice,
                        money(unitPrice.multiply(BigDecimal.valueOf(units)))));
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDateTime localDateTime(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record OrderRow(
            UUID orderId,
            long orderNumber,
            String status,
            LocalDateTime createdAt,
            String currency,
            BuyerOrderResponse.Payment payment,
            BuyerOrderResponse.DeliveryAddress address
    ) {
    }

    private record SaleRow(
            UUID saleId,
            UUID storeId,
            String storeName,
            UUID branchId,
            String branchName,
            String status,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal total
    ) {
    }

    private record ItemRow(UUID saleId, BuyerOrderResponse.Item item) {
    }

    private record SummaryRow(
            UUID orderId,
            long orderNumber,
            String status,
            LocalDateTime createdAt,
            String currency,
            BigDecimal total,
            int itemCount
    ) {
    }

    private record SaleRefRow(UUID orderId, BuyerOrderSummary.SaleRef ref) {
    }

    private record ShipmentRow(
            UUID shipmentId,
            UUID saleId,
            String carrier,
            String trackingNumber,
            String status,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt
    ) {
    }

    private record EventRow(UUID shipmentId, BuyerOrderResponse.Shipment.Event event) {
    }
}
