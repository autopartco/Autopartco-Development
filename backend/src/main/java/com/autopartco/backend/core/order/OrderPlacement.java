package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
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
    static final String SALE_STATUS = "PENDING";
    static final String PAYMENT_STATUS = "APPROVED";
    static final String PAYMENT_EVENT_MESSAGE = "Pago simulado aprobado";

    static final String ACTIVE_PAYMENT_METHOD_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM payment_methods
                WHERE code = :code AND active = true AND deleted_at IS NULL
            )
            """;

    static final String PAYMENT_REFERENCE_OWNER_SQL = """
            SELECT payment.order_id, customer_order.person_id
            FROM payments payment
            JOIN orders customer_order ON customer_order.id = payment.order_id
            WHERE payment.provider = :provider
              AND payment.provider_reference = :reference
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

    /** Pago con provider {@code SIMULATED} y esa referencia, si existe. */
    @Transactional(readOnly = true)
    public Optional<PaymentReferenceOwner> findByPaymentReference(String reference) {
        return jdbcClient.sql(PAYMENT_REFERENCE_OWNER_SQL)
                .param("provider", PROVIDER)
                .param("reference", reference)
                .query((result, row) -> new PaymentReferenceOwner(
                        result.getObject("order_id", UUID.class),
                        result.getObject("person_id", UUID.class)))
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
        UUID orderId = jdbcClient.sql(INSERT_ORDER_SQL)
                .param("personId", order.personId())
                .param("stateCode", ORDER_STATUS)
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
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        UUID paymentId = jdbcClient.sql(INSERT_PAYMENT_SQL)
                .param("orderId", orderId)
                .param("methodCode", order.paymentMethodCode())
                .param("statusCode", PAYMENT_STATUS)
                .param("provider", PROVIDER)
                .param("reference", order.paymentReference())
                .param("amount", amount)
                .param("currency", CURRENCY)
                .query(UUID.class)
                .single();

        jdbcClient.sql(INSERT_PAYMENT_EVENT_SQL)
                .param("paymentId", paymentId)
                .param("statusCode", PAYMENT_STATUS)
                .param("message", PAYMENT_EVENT_MESSAGE)
                .update();

        return orderId;
    }
}
