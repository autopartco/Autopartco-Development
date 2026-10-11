package com.autopartco.backend.seller.service;

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

import com.autopartco.backend.exception.ConflictException;
import com.autopartco.backend.exception.NotFoundException;
import com.autopartco.backend.seller.dto.SellerDashboardResponse;
import com.autopartco.backend.seller.dto.SellerNotificationResponse;
import com.autopartco.backend.seller.dto.SellerOrderResponse;
import com.autopartco.backend.seller.dto.SellerOrderStatusRequest;
import com.autopartco.backend.seller.dto.SellerOrderSummaryResponse;
import com.autopartco.backend.seller.dto.SellerShipmentCreateRequest;
import com.autopartco.backend.seller.dto.SellerShipmentStatusRequest;
import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService.Access;
import com.autopartco.backend.seller.service.SellerPlanEntitlementService.Usage;
import com.autopartco.backend.store.service.StoreAccessService;
import com.autopartco.backend.store.service.StorePermissionCodes;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerDashboardService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final RowMapper<SellerOrderSummaryResponse> ORDER_SUMMARY_MAPPER = (result, row) ->
            new SellerOrderSummaryResponse(
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
    private final StoreAccessService storeAccessService;
    private final SellerPlanEntitlementService entitlementService;
    private final SellerPlanService planService;

    @Transactional(readOnly = true)
    public SellerDashboardResponse dashboard(UUID storeId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        Access access = entitlementService.requireAccess(storeId);
        SellerApplication application = access.application();
        Usage usage = entitlementService.usage(storeId);
        String storeStatus = application.getStore().getState().getCode();
        SellerDashboardResponse.SalesSummary sales = jdbcTemplate.queryForObject("""
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
                """, (result, row) -> new SellerDashboardResponse.SalesSummary(
                result.getLong("month_orders"),
                result.getBigDecimal("month_revenue"),
                result.getLong("pending_orders"),
                result.getLong("preparing_orders")
        ), storeId);

        return new SellerDashboardResponse(
                storeId,
                application.getStore().getBusinessName(),
                storeStatus,
                planService.toResponse(access.plan()),
                planService.toResponse(application.getSelectedPlan()),
                application.getConfigurationCompletedAt() == null,
                access.subscriptionExpired(),
                application.getTrialEndsAt(),
                application.getPaidThrough(),
                access.plan().getMaxBranches(),
                access.plan().getMaxProducts(),
                access.plan().getMaxUsers(),
                access.capabilities(),
                new SellerDashboardResponse.Usage(usage.branches(), usage.products(), usage.users()),
                sales
        );
    }

    @Transactional(readOnly = true)
    public List<SellerOrderSummaryResponse> orders(
            UUID storeId,
            String email,
            String status,
            int page,
            int size
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
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

    @Transactional(readOnly = true)
    public SellerOrderResponse order(UUID storeId, UUID saleId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
        return loadOrder(storeId, saleId);
    }

    @Transactional
    public SellerOrderResponse updateOrderStatus(
            UUID storeId,
            UUID saleId,
            String email,
            SellerOrderStatusRequest request
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.ORDER_UPDATE);
        String currentStatus = lockSaleStatus(storeId, saleId);
        String nextStatus = request.status().toUpperCase(Locale.ROOT);
        if (!isOrderTransitionAllowed(currentStatus, nextStatus)) {
            throw new ConflictException(
                    "SELLER_ORDER_STATUS_TRANSITION_INVALID",
                    "No se puede cambiar el pedido de " + currentStatus + " a " + nextStatus + "."
            );
        }
        updateSaleStatus(saleId, nextStatus);
        notifyCustomer(saleId, "Pedido actualizado", "Tu pedido cambió al estado " + nextStatus + ".");
        return loadOrder(storeId, saleId);
    }

    @Transactional
    public SellerOrderResponse createShipment(
            UUID storeId,
            UUID saleId,
            String email,
            SellerShipmentCreateRequest request
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.SHIPMENT_UPDATE);
        String saleStatus = lockSaleStatus(storeId, saleId);
        if (!"PREPARING".equals(saleStatus)) {
            throw new ConflictException(
                    "SELLER_SHIPMENT_ORDER_NOT_PREPARING",
                    "Solo se puede registrar el envío cuando la venta está en preparación."
            );
        }
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM shipments WHERE sale_id = ?)",
                Boolean.class,
                saleId
        ))) {
            throw new ConflictException("SELLER_SHIPMENT_ALREADY_EXISTS", "La venta ya tiene un envío registrado.");
        }
        UUID shipmentId = jdbcTemplate.queryForObject("""
                INSERT INTO shipments (
                    sale_id, shipment_status_id, carrier, tracking_number
                )
                VALUES (
                    ?, (SELECT id FROM shipment_statuses WHERE code = 'PREPARING'),
                    ?, ?
                )
                RETURNING id
                """, UUID.class, saleId, request.carrier().trim(), request.trackingNumber().trim());
        jdbcTemplate.update("""
                INSERT INTO shipment_events (shipment_id, shipment_status_id, notes, event_at)
                VALUES (
                    ?, (SELECT id FROM shipment_statuses WHERE code = 'PREPARING'),
                    'Envío registrado', now()
                )
                """, shipmentId);
        notifyCustomer(saleId, "Envío registrado", "Se registró el envío de tu pedido.");
        return loadOrder(storeId, saleId);
    }

    @Transactional
    public SellerOrderResponse updateShipmentStatus(
            UUID storeId,
            UUID saleId,
            String email,
            SellerShipmentStatusRequest request
    ) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.SHIPMENT_UPDATE);
        lockSaleStatus(storeId, saleId);
        ShipmentRow shipment = jdbcTemplate.query("""
                SELECT shipment.id, shipment_status.code AS status
                FROM shipments shipment
                JOIN shipment_statuses shipment_status ON shipment_status.id = shipment.shipment_status_id
                WHERE shipment.sale_id = ?
                FOR UPDATE OF shipment
                """, (result, row) -> new ShipmentRow(
                result.getObject("id", UUID.class),
                result.getString("status")
        ), saleId).stream().findFirst().orElseThrow(() -> new NotFoundException(
                "SELLER_SHIPMENT_NOT_FOUND",
                "No se encontró el envío de esta venta."
        ));
        String nextStatus = request.status().toUpperCase(Locale.ROOT);
        if (!isShipmentTransitionAllowed(shipment.status(), nextStatus)) {
            throw new ConflictException(
                    "SELLER_SHIPMENT_STATUS_TRANSITION_INVALID",
                    "No se puede cambiar el envío de " + shipment.status() + " a " + nextStatus + "."
            );
        }
        jdbcTemplate.update("""
                UPDATE shipments
                SET shipment_status_id = (SELECT id FROM shipment_statuses WHERE code = ?),
                    shipped_at = CASE WHEN ? = 'DISPATCHED' THEN COALESCE(shipped_at, now()) ELSE shipped_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN now() ELSE delivered_at END,
                    updated_at = now()
                WHERE id = ?
                """, nextStatus, nextStatus, nextStatus, shipment.id());
        jdbcTemplate.update("""
                INSERT INTO shipment_events (shipment_id, shipment_status_id, notes, event_at)
                VALUES (?, (SELECT id FROM shipment_statuses WHERE code = ?), ?, now())
                """, shipment.id(), nextStatus, cleanNotes(request.notes()));
        if ("DISPATCHED".equals(nextStatus)) {
            updateSaleStatus(saleId, "SHIPPED");
        } else if ("DELIVERED".equals(nextStatus)) {
            updateSaleStatus(saleId, "DELIVERED");
        }
        notifyCustomer(saleId, "Estado del envío actualizado", "Tu envío cambió al estado " + nextStatus + ".");
        return loadOrder(storeId, saleId);
    }

    @Transactional(readOnly = true)
    public List<SellerNotificationResponse> notifications(String email) {
        return jdbcTemplate.query("""
                SELECT notification.id, notification.title, notification.message,
                       notification.read_at, notification.created_at
                FROM notifications notification
                JOIN accounts account ON account.id = notification.account_id
                WHERE lower(account.email) = lower(?)
                ORDER BY notification.created_at DESC
                LIMIT 50
                """, (result, row) -> new SellerNotificationResponse(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("message"),
                result.getTimestamp("read_at") != null,
                localDateTime(result, "created_at")
        ), email);
    }

    @Transactional
    public void markNotificationRead(UUID notificationId, String email) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications notification
                SET read_at = COALESCE(read_at, now())
                FROM accounts account
                WHERE notification.id = ?
                  AND notification.account_id = account.id
                  AND lower(account.email) = lower(?)
                """, notificationId, email);
        if (updated == 0) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "No se encontró la notificación.");
        }
    }

    private SellerOrderResponse loadOrder(UUID storeId, UUID saleId) {
        SellerOrderBase base = jdbcTemplate.query("""
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
                """, (result, row) -> new SellerOrderBase(
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

        List<SellerOrderResponse.Item> items = jdbcTemplate.query("""
                SELECT detail.inventory_id, product.id AS product_id, product.name,
                       product.part_number, detail.units, detail.unit_price
                FROM order_details detail
                JOIN inventory ON inventory.id = detail.inventory_id
                JOIN products product ON product.id = inventory.product_id
                WHERE detail.sale_id = ?
                ORDER BY product.name
                """, (result, row) -> new SellerOrderResponse.Item(
                result.getObject("inventory_id", UUID.class),
                result.getObject("product_id", UUID.class),
                result.getString("name"),
                result.getString("part_number"),
                result.getInt("units"),
                result.getBigDecimal("unit_price")
        ), saleId);

        SellerOrderResponse.Shipment shipment = null;
        if (base.shipmentId() != null) {
            List<SellerOrderResponse.Event> events = jdbcTemplate.query("""
                    SELECT status.code, event.notes, event.event_at
                    FROM shipment_events event
                    JOIN shipment_statuses status ON status.id = event.shipment_status_id
                    WHERE event.shipment_id = ?
                    ORDER BY event.event_at, event.id
                    """, (result, row) -> new SellerOrderResponse.Event(
                    result.getString("code"),
                    result.getString("notes"),
                    localDateTime(result, "event_at")
            ), base.shipmentId());
            shipment = new SellerOrderResponse.Shipment(
                    base.shipmentId(),
                    base.carrier(),
                    base.trackingNumber(),
                    base.shipmentStatus(),
                    base.shippedAt(),
                    base.deliveredAt(),
                    events
            );
        }
        SellerOrderResponse.DeliveryAddress address = base.addressLine() == null
                ? null
                : new SellerOrderResponse.DeliveryAddress(
                        base.addressLine(),
                        base.addressComplement(),
                        base.city(),
                        base.department(),
                        base.country(),
                        base.postalCode()
                );
        return new SellerOrderResponse(
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

    private String lockSaleStatus(UUID storeId, UUID saleId) {
        return jdbcTemplate.query("""
                SELECT status.code
                FROM sales sale
                JOIN branches branch ON branch.id = sale.branch_id
                JOIN states status ON status.id = sale.state_id
                WHERE branch.store_id = ?
                  AND sale.id = ?
                  AND sale.deleted_at IS NULL
                FOR UPDATE OF sale
                """, (result, row) -> result.getString(1), storeId, saleId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_ORDER_NOT_FOUND",
                        "No se encontró la venta de este almacén."
                ));
    }

    private void updateSaleStatus(UUID saleId, String status) {
        int updated = jdbcTemplate.update("""
                UPDATE sales
                SET state_id = (SELECT id FROM states WHERE code = ?),
                    updated_at = now()
                WHERE id = ?
                  AND deleted_at IS NULL
                """, status, saleId);
        if (updated != 1) {
            throw new NotFoundException("SELLER_ORDER_NOT_FOUND", "No se encontró la venta.");
        }
    }

    private void notifyCustomer(UUID saleId, String title, String message) {
        jdbcTemplate.update("""
                INSERT INTO notifications (account_id, title, message)
                SELECT account.id, ?, ?
                FROM sales sale
                JOIN orders customer_order ON customer_order.id = sale.order_id
                JOIN accounts account ON account.id_person = customer_order.person_id
                WHERE sale.id = ?
                """, title, message, saleId);
    }

    static boolean isOrderTransitionAllowed(String current, String next) {
        return switch (current) {
            case "PENDING" -> next.equals("CONFIRMED") || next.equals("CANCELLED");
            case "CONFIRMED" -> next.equals("PREPARING") || next.equals("CANCELLED");
            default -> false;
        };
    }

    static boolean isShipmentTransitionAllowed(String current, String next) {
        return switch (current) {
            case "PREPARING" -> next.equals("DISPATCHED");
            case "DISPATCHED" -> next.equals("IN_TRANSIT")
                    || next.equals("OUT_FOR_DELIVERY")
                    || next.equals("DELIVERED");
            case "IN_TRANSIT" -> next.equals("OUT_FOR_DELIVERY") || next.equals("DELIVERED");
            case "OUT_FOR_DELIVERY" -> next.equals("DELIVERED");
            default -> false;
        };
    }

    private String cleanNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }

    private static LocalDateTime localDateTime(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static UUID optionalUuid(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : result.getObject(column, UUID.class);
    }

    private record ShipmentRow(UUID id, String status) {
    }

    private record SellerOrderBase(
            UUID saleId,
            UUID orderId,
            long orderNumber,
            UUID branchId,
            String branchName,
            String status,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal shippingCost,
            java.math.BigDecimal total,
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
