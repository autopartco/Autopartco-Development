package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/** Lecturas de ventas de un almacén: listado, detalle y resumen mensual. No comprueba permisos. */
@Service
@AllArgsConstructor
public class SaleQueries {
    private static final int MAX_PAGE_SIZE = 100;
    private static final RowMapper<SaleSummaryResponse> ORDER_SUMMARY_MAPPER = (result, row) ->
            new SaleSummaryResponse(
                    result.getObject("sale_id", UUID.class),
                    result.getObject("order_id", UUID.class),
                    result.getLong("order_number"),
                    result.getObject("branch_id", UUID.class),
                    result.getString("branch_name"),
                    result.getString("sale_status"),
                    result.getBigDecimal("subtotal"),
                    result.getBigDecimal("shipping_cost"),
                    result.getBigDecimal("total"),
                    localDateTime(result, "created_at"),
                    result.getString("customer_name"),
                    result.getString("payment_status"),
                    result.getString("shipment_status"),
                    result.getInt("item_count")
            );

    private static final String ORDER_SELECT = """
            SELECT sale.id AS sale_id,
                   sale.order_id,
                   customer_order.order_number,
                   branch.id AS branch_id,
                   branch.name AS branch_name,
                   sale_state.code AS sale_status,
                   sale.subtotal,
                   sale.shipping_cost,
                   sale.total,
                   sale.created_at,
                   concat(person.name, ' ', person.surname) AS customer_name,
                   payment.payment_status,
                   shipment.shipment_status,
                   (SELECT count(*) FROM order_details detail WHERE detail.sale_id = sale.id) AS item_count
            FROM sales sale
            JOIN branches branch ON branch.id = sale.branch_id
            JOIN orders customer_order ON customer_order.id = sale.order_id
            JOIN states sale_state ON sale_state.id = sale.state_id
            JOIN persons person ON person.id = customer_order.person_id
            LEFT JOIN LATERAL (
                SELECT status.code AS payment_status
                FROM payments payment_record
                JOIN payment_statuses status ON status.id = payment_record.payment_status_id
                WHERE payment_record.order_id = customer_order.id
                ORDER BY payment_record.created_at DESC
                LIMIT 1
            ) payment ON true
            LEFT JOIN LATERAL (
                SELECT status.code AS shipment_status
                FROM shipments shipment_record
                JOIN shipment_statuses status ON status.id = shipment_record.shipment_status_id
                WHERE shipment_record.sale_id = sale.id
                LIMIT 1
            ) shipment ON true
            WHERE branch.store_id = ?
              AND sale.deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    /** Pedidos del mes en curso, ingresos (sin cancelados) y pedidos pendientes o en preparación. */
    @Transactional(readOnly = true)
    public SalesSummary monthlySummary(UUID storeId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE sale.created_at >= date_trunc('month', now())) AS month_orders,
                       COALESCE(sum(sale.total) FILTER (
                           WHERE sale.created_at >= date_trunc('month', now())
                             AND sale_state.code <> 'CANCELLED'
                       ), 0) AS month_revenue,
                       count(*) FILTER (WHERE sale_state.code = 'PENDING') AS pending_orders,
                       count(*) FILTER (WHERE sale_state.code = 'PREPARING') AS preparing_orders
                FROM sales sale
                JOIN branches branch ON branch.id = sale.branch_id
                JOIN states sale_state ON sale_state.id = sale.state_id
                WHERE branch.store_id = ?
                  AND sale.deleted_at IS NULL
                """, (result, row) -> new SalesSummary(
                result.getLong("month_orders"),
                result.getBigDecimal("month_revenue"),
                result.getLong("pending_orders"),
                result.getLong("preparing_orders")
        ), storeId);
    }

    /** Ventas del almacén, más recientes primero; {@code status} opcional, tamaño de página entre 1 y 100. */
    @Transactional(readOnly = true)
    public List<SaleSummaryResponse> list(UUID storeId, String status, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        StringBuilder query = new StringBuilder(ORDER_SELECT);
        if (status != null && !status.isBlank()) {
            query.append(" AND sale_state.code = ?");
        }
        query.append(" ORDER BY sale.created_at DESC, sale.id LIMIT ? OFFSET ?");
        Object[] args;
        if (status == null || status.isBlank()) {
            args = new Object[] { storeId, safeSize, (long) safePage * safeSize };
        } else {
            args = new Object[] {
                    storeId,
                    status.trim().toUpperCase(Locale.ROOT),
                    safeSize,
                    (long) safePage * safeSize
            };
        }
        return jdbcTemplate.query(query.toString(), ORDER_SUMMARY_MAPPER, args);
    }

    /** Detalle de una venta del almacén; si no existe, {@code SELLER_ORDER_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public SaleDetailResponse detail(UUID storeId, UUID saleId) {
        SaleBase base = jdbcTemplate.query("""
                SELECT sale.id AS sale_id,
                       sale.order_id,
                       customer_order.order_number,
                       branch.id AS branch_id,
                       branch.name AS branch_name,
                       sale_state.code AS sale_status,
                       sale.subtotal,
                       sale.shipping_cost,
                       sale.total,
                       sale.created_at,
                       concat(person.name, ' ', person.surname) AS customer_name,
                       payment.payment_status,
                       payment.payment_method,
                       shipment.id AS shipment_id,
                       shipment.carrier,
                       shipment.tracking_number,
                       shipment.shipment_status,
                       shipment.shipped_at,
                       shipment.delivered_at,
                       delivery.address_line,
                       delivery.address_complement,
                       delivery.city,
                       delivery.department,
                       delivery.country,
                       delivery.postal_code
                FROM sales sale
                JOIN branches branch ON branch.id = sale.branch_id
                JOIN orders customer_order ON customer_order.id = sale.order_id
                JOIN states sale_state ON sale_state.id = sale.state_id
                JOIN persons person ON person.id = customer_order.person_id
                LEFT JOIN order_addresses delivery ON delivery.order_id = customer_order.id
                LEFT JOIN LATERAL (
                    SELECT status.code AS payment_status, method.name AS payment_method
                    FROM payments payment_record
                    JOIN payment_statuses status ON status.id = payment_record.payment_status_id
                    JOIN payment_methods method ON method.id = payment_record.payment_method_id
                    WHERE payment_record.order_id = customer_order.id
                    ORDER BY payment_record.created_at DESC
                    LIMIT 1
                ) payment ON true
                LEFT JOIN LATERAL (
                    SELECT shipment_record.id, shipment_record.carrier,
                           shipment_record.tracking_number, status.code AS shipment_status,
                           shipment_record.shipped_at, shipment_record.delivered_at
                    FROM shipments shipment_record
                    JOIN shipment_statuses status ON status.id = shipment_record.shipment_status_id
                    WHERE shipment_record.sale_id = sale.id
                    LIMIT 1
                ) shipment ON true
                WHERE branch.store_id = ?
                  AND sale.id = ?
                  AND sale.deleted_at IS NULL
                """, (result, row) -> new SaleBase(
                result.getObject("sale_id", UUID.class),
                result.getObject("order_id", UUID.class),
                result.getLong("order_number"),
                result.getObject("branch_id", UUID.class),
                result.getString("branch_name"),
                result.getString("sale_status"),
                result.getBigDecimal("subtotal"),
                result.getBigDecimal("shipping_cost"),
                result.getBigDecimal("total"),
                localDateTime(result, "created_at"),
                result.getString("customer_name"),
                result.getString("payment_status"),
                result.getString("payment_method"),
                optionalUuid(result, "shipment_id"),
                result.getString("carrier"),
                result.getString("tracking_number"),
                result.getString("shipment_status"),
                localDateTime(result, "shipped_at"),
                localDateTime(result, "delivered_at"),
                result.getString("address_line"),
                result.getString("address_complement"),
                result.getString("city"),
                result.getString("department"),
                result.getString("country"),
                result.getString("postal_code")
        ), storeId, saleId).stream().findFirst().orElseThrow(() -> new NotFoundException(
                "SELLER_ORDER_NOT_FOUND",
                "No se encontró la venta de este almacén."
        ));

        List<SaleDetailResponse.Item> items = jdbcTemplate.query("""
                SELECT detail.inventory_id, product.id AS product_id, product.name,
                       product.part_number, detail.units, detail.unit_price
                FROM order_details detail
                JOIN inventory ON inventory.id = detail.inventory_id
                JOIN products product ON product.id = inventory.product_id
                WHERE detail.sale_id = ?
                ORDER BY product.name
                """, (result, row) -> new SaleDetailResponse.Item(
                result.getObject("inventory_id", UUID.class),
                result.getObject("product_id", UUID.class),
                result.getString("name"),
                result.getString("part_number"),
                result.getInt("units"),
                result.getBigDecimal("unit_price")
        ), saleId);

        SaleDetailResponse.Shipment shipment = null;
        if (base.shipmentId() != null) {
            List<SaleDetailResponse.Event> events = jdbcTemplate.query("""
                    SELECT status.code, event.notes, event.event_at
                    FROM shipment_events event
                    JOIN shipment_statuses status ON status.id = event.shipment_status_id
                    WHERE event.shipment_id = ?
                    ORDER BY event.event_at, event.id
                    """, (result, row) -> new SaleDetailResponse.Event(
                    result.getString("code"),
                    result.getString("notes"),
                    localDateTime(result, "event_at")
            ), base.shipmentId());
            shipment = new SaleDetailResponse.Shipment(
                    base.shipmentId(),
                    base.carrier(),
                    base.trackingNumber(),
                    base.shipmentStatus(),
                    base.shippedAt(),
                    base.deliveredAt(),
                    events
            );
        }
        SaleDetailResponse.DeliveryAddress address = base.addressLine() == null
                ? null
                : new SaleDetailResponse.DeliveryAddress(
                        base.addressLine(),
                        base.addressComplement(),
                        base.city(),
                        base.department(),
                        base.country(),
                        base.postalCode()
                );
        return new SaleDetailResponse(
                base.saleId(),
                base.orderId(),
                base.orderNumber(),
                base.branchId(),
                base.branchName(),
                base.status(),
                base.subtotal(),
                base.shippingCost(),
                base.total(),
                base.createdAt(),
                base.customerName(),
                base.paymentStatus(),
                base.paymentMethod(),
                shipment,
                items,
                address
        );
    }

    private static LocalDateTime localDateTime(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static UUID optionalUuid(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : result.getObject(column, UUID.class);
    }

    private record SaleBase(
            UUID saleId,
            UUID orderId,
            long orderNumber,
            UUID branchId,
            String branchName,
            String status,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal total,
            LocalDateTime createdAt,
            String customerName,
            String paymentStatus,
            String paymentMethod,
            UUID shipmentId,
            String carrier,
            String trackingNumber,
            String shipmentStatus,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            String addressLine,
            String addressComplement,
            String city,
            String department,
            String country,
            String postalCode
    ) {
    }
}
