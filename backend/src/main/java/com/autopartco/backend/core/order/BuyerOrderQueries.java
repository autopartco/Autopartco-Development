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

import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Lecturas del pedido desde el punto de vista del comprador, con {@link JdbcClient}. Siempre filtra por la persona
 * dueña del pedido; un pedido ajeno se trata igual que uno inexistente. B2 añadirá aquí el listado y el detalle.
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
                   store.bussnies_name AS store_name,
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
            ORDER BY store.bussnies_name, branch.name, sale.id
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

    private final JdbcClient jdbcClient;

    /** Pedido de esa persona con la forma del contrato; si no existe o es ajeno, {@code ORDER_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public BuyerOrderResponse receipt(UUID personId, UUID orderId) {
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
                    List.copyOf(itemsBySale.getOrDefault(sale.saleId(), List.of()))
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
}
