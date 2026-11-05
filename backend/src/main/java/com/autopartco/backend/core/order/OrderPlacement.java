package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Escritura del pedido del comprador con {@link JdbcClient} (sin entidades JPA sobre las tablas de pedido). No valida
 * permisos, precios ni stock: lo hace el caso de uso antes de llamar. Los identificadores de estados, métodos y
 * estados de pago se resuelven por código dentro del propio SQL.
 */
@Service
@AllArgsConstructor
public class OrderPlacement {
    static final String PROVIDER = "SIMULATED";
    static final String CURRENCY = "COP";
    static final String ORDER_STATUS = "CONFIRMED";
    static final String AWAITING_PAYMENT_STATUS = "AWAITING_PAYMENT";
    static final String SALE_STATUS = "PENDING";
    static final String PAYMENT_STATUS = "APPROVED";
    static final String PENDING_PAYMENT_STATUS = "PENDING";
    static final String PAYMENT_EVENT_MESSAGE = "Pago simulado aprobado";
    static final String PENDING_PAYMENT_EVENT_MESSAGE = "Pago iniciado; en espera del proveedor";

    static final String ACTIVE_PAYMENT_METHOD_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM payment_methods
                WHERE code = :code AND active = true AND deleted_at IS NULL
            )
            """;

    static final String ACTIVE_PAYMENT_METHODS_SQL = """
            SELECT code, name
            FROM payment_methods
            WHERE active = true AND deleted_at IS NULL
              AND starts_with(code, :prefix)
            ORDER BY code
            """;

    static final String PAYMENT_REFERENCE_OWNER_SQL = """
            SELECT payment.order_id, customer_order.person_id
            FROM payments payment
            JOIN orders customer_order ON customer_order.id = payment.order_id
            WHERE payment.provider = :provider
              AND payment.provider_reference = :reference
            """;

    static final String IDEMPOTENCY_KEY_OWNER_SQL = """
            SELECT payment.order_id, customer_order.person_id
            FROM payments payment
            JOIN orders customer_order ON customer_order.id = payment.order_id
            WHERE payment.idempotency_key = :idempotencyKey
            """;

    static final String INSERT_ORDER_SQL = """
            INSERT INTO orders (person_id, state_id)
            VALUES (:personId, (SELECT id FROM states WHERE code = :stateCode))
            RETURNING id
            """;

    static final String INSERT_ORDER_ADDRESS_SQL = """
            INSERT INTO order_addresses (order_id, address_line, address_complement, city, department, country,
                                         postal_code, latitude, longitude)
            VALUES (:orderId, :addressLine, :addressComplement, :city, :department, :country,
                    :postalCode, :latitude, :longitude)
            """;

    static final String INSERT_SALE_SQL = """
            INSERT INTO sales (order_id, branch_id, state_id, subtotal, shipping_cost, total)
            VALUES (:orderId, :branchId, (SELECT id FROM states WHERE code = :stateCode),
                    :subtotal, :shippingCost, :total)
            RETURNING id
            """;

    static final String INSERT_ORDER_DETAIL_SQL = """
            INSERT INTO order_details (order_id, sale_id, inventory_id, product_id, units, unit_price)
            VALUES (:orderId, :saleId, :inventoryId, :productId, :units, :unitPrice)
            """;

    static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payments (order_id, payment_method_id, payment_status_id, provider, provider_reference,
                                  amount, currency, paid_at)
            VALUES (:orderId,
                    (SELECT id FROM payment_methods WHERE code = :methodCode),
                    (SELECT id FROM payment_statuses WHERE code = :statusCode),
                    :provider, :reference, :amount, :currency, now())
            RETURNING id
            """;

    static final String INSERT_PENDING_PAYMENT_SQL = """
            INSERT INTO payments (order_id, payment_method_id, payment_status_id, provider, provider_reference,
                                  amount, currency, idempotency_key, environment, expires_at)
            VALUES (:orderId,
                    (SELECT id FROM payment_methods WHERE code = :methodCode),
                    (SELECT id FROM payment_statuses WHERE code = :statusCode),
                    :provider, :reference, :amount, :currency, :idempotencyKey, :environment, :expiresAt)
            RETURNING id
            """;

    static final String INSERT_PAYMENT_EVENT_SQL = """
            INSERT INTO payment_events (payment_id, payment_status_id, message)
            VALUES (:paymentId, (SELECT id FROM payment_statuses WHERE code = :statusCode), :message)
            """;

    private final JdbcClient jdbcClient;

    /** {@code true} si el método de pago existe, está activo y no está borrado. */
    @Transactional(readOnly = true)
    public boolean isActivePaymentMethod(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcClient.sql(ACTIVE_PAYMENT_METHOD_SQL)
                .param("code", code)
                .query(Boolean.class)
                .single());
    }

    /** Métodos de pago activos cuyo código empieza por {@code prefix} (p. ej. {@code SIMULATED_}), por código. */
    @Transactional(readOnly = true)
    public List<PaymentMethodOption> activePaymentMethods(String prefix) {
        return jdbcClient.sql(ACTIVE_PAYMENT_METHODS_SQL)
                .param("prefix", prefix)
                .query((result, row) -> new PaymentMethodOption(result.getString("code"), result.getString("name")))
                .list();
    }

    /** Pago con provider {@code SIMULATED} y esa referencia, si existe. */
    @Transactional(readOnly = true)
    public Optional<PaymentReferenceOwner> findByPaymentReference(String reference) {
        return jdbcClient.sql(PAYMENT_REFERENCE_OWNER_SQL)
                .param("provider", PROVIDER)
                .param("reference", reference)
                .query(OrderPlacement::mapOwner)
                .list()
                .stream()
                .findFirst();
    }

    /** Pago (de cualquier proveedor) con esa {@code idempotency_key} ({@code clientRequestId}), si existe. */
    @Transactional(readOnly = true)
    public Optional<PaymentReferenceOwner> findByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql(IDEMPOTENCY_KEY_OWNER_SQL)
                .param("idempotencyKey", idempotencyKey)
                .query(OrderPlacement::mapOwner)
                .list()
                .stream()
                .findFirst();
    }

    /**
     * Inserta {@code orders} (CONFIRMED), {@code order_addresses}, por venta {@code sales} (PENDING) y sus
     * {@code order_details}, {@code payments} (APPROVED, provider SIMULATED, COP, monto = suma de los totales de las
     * ventas, {@code paid_at = now()}) y {@code payment_events} (APPROVED). Devuelve el id del pedido. Si la referencia
     * de pago está repetida, propaga la {@code DuplicateKeyException}.
     */
    @Transactional
    public UUID placeApproved(NewOrder order) {
        PlacedOrder placed = insertOrder(order, ORDER_STATUS);

        UUID paymentId = jdbcClient.sql(INSERT_PAYMENT_SQL)
                .param("orderId", placed.orderId())
                .param("methodCode", order.paymentMethodCode())
                .param("statusCode", PAYMENT_STATUS)
                .param("provider", PROVIDER)
                .param("reference", order.paymentReference())
                .param("amount", placed.amount())
                .param("currency", CURRENCY)
                .query(UUID.class)
                .single();

        insertPaymentEvent(paymentId, PAYMENT_STATUS, PAYMENT_EVENT_MESSAGE);
        return placed.orderId();
    }

    /**
     * Inserta el pedido en espera de pago (ADR 0004 §2.4): {@code orders} (AWAITING_PAYMENT), dirección, ventas
     * (PENDING) y detalles, {@code payments} (PENDING, sin {@code paid_at}, con referencia, {@code idempotency_key},
     * entorno y {@code expires_at}) y {@code payment_events} (PENDING). {@code order.paymentReference()} se ignora:
     * manda {@code payment.reference()}. Una referencia o clave de idempotencia repetida propaga la
     * {@code DuplicateKeyException}.
     */
    @Transactional
    public UUID placeAwaitingPayment(NewOrder order, PendingPayment payment) {
        PlacedOrder placed = insertOrder(order, AWAITING_PAYMENT_STATUS);

        UUID paymentId = jdbcClient.sql(INSERT_PENDING_PAYMENT_SQL)
                .param("orderId", placed.orderId())
                .param("methodCode", order.paymentMethodCode())
                .param("statusCode", PENDING_PAYMENT_STATUS)
                .param("provider", payment.provider())
                .param("reference", payment.reference())
                .param("amount", placed.amount())
                .param("currency", CURRENCY)
                .param("idempotencyKey", payment.idempotencyKey())
                .param("environment", payment.environment())
                .param("expiresAt", OffsetDateTime.ofInstant(payment.expiresAt(), ZoneOffset.UTC))
                .query(UUID.class)
                .single();

        insertPaymentEvent(paymentId, PENDING_PAYMENT_STATUS, PENDING_PAYMENT_EVENT_MESSAGE);
        return placed.orderId();
    }

    private PlacedOrder insertOrder(NewOrder order, String orderStatus) {
        UUID orderId = jdbcClient.sql(INSERT_ORDER_SQL)
                .param("personId", order.personId())
                .param("stateCode", orderStatus)
                .query(UUID.class)
                .single();

        NewOrder.DeliveryAddress address = order.address();
        jdbcClient.sql(INSERT_ORDER_ADDRESS_SQL)
                .param("orderId", orderId)
                .param("addressLine", address.addressLine())
                .param("addressComplement", address.addressComplement(), Types.VARCHAR)
                .param("city", address.city())
                .param("department", address.department())
                .param("country", address.country())
                .param("postalCode", address.postalCode(), Types.VARCHAR)
                .param("latitude", address.latitude(), Types.NUMERIC)
                .param("longitude", address.longitude(), Types.NUMERIC)
                .update();

        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        for (NewOrder.NewSale sale : order.sales()) {
            UUID saleId = jdbcClient.sql(INSERT_SALE_SQL)
                    .param("orderId", orderId)
                    .param("branchId", sale.branchId())
                    .param("stateCode", SALE_STATUS)
                    .param("subtotal", sale.subtotal())
                    .param("shippingCost", sale.shippingCost())
                    .param("total", sale.total())
                    .query(UUID.class)
                    .single();
            for (NewOrder.NewLine line : sale.lines()) {
                jdbcClient.sql(INSERT_ORDER_DETAIL_SQL)
                        .param("orderId", orderId)
                        .param("saleId", saleId)
                        .param("inventoryId", line.inventoryId())
                        .param("productId", line.productId())
                        .param("units", line.units())
                        .param("unitPrice", line.unitPrice())
                        .update();
            }
            amount = amount.add(sale.total());
        }
        return new PlacedOrder(orderId, amount.setScale(2, RoundingMode.HALF_UP));
    }

    private void insertPaymentEvent(UUID paymentId, String statusCode, String message) {
        jdbcClient.sql(INSERT_PAYMENT_EVENT_SQL)
                .param("paymentId", paymentId)
                .param("statusCode", statusCode)
                .param("message", message)
                .update();
    }

    private static PaymentReferenceOwner mapOwner(java.sql.ResultSet result, int row) throws java.sql.SQLException {
        return new PaymentReferenceOwner(
                result.getObject("order_id", UUID.class),
                result.getObject("person_id", UUID.class));
    }

    private record PlacedOrder(UUID orderId, BigDecimal amount) {
    }
}
