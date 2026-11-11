package com.autopartco.backend.core.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.events.OrderPaid;

/**
 * Estado del pago de un pedido con un proveedor asíncrono (Wompi, ADR 0004) y sus efectos sobre el pedido, con
 * {@link JdbcClient}. El orden de bloqueo es siempre pago → pedido → inventario, tanto al aplicar una transacción del
 * proveedor como al vencer la reserva: así una aprobación y un vencimiento simultáneos tienen un solo ganador.
 * <p>
 * No decide reglas de negocio (lo hace {@code marketplace.checkout}) ni valida permisos. Solo usa columnas del esquema
 * activo ({@code database/init.sql}); no escribe {@code payment_events.source} ni {@code order_status_history}.
 */
@Service
public class OrderPayments {
    public static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";

    static final String PAYMENT_COLUMNS = """
            SELECT payment.id AS payment_id,
                   payment.order_id,
                   customer_order.person_id,
                   customer_order.order_number,
                   order_state.code AS order_status,
                   payment.provider,
                   payment.provider_reference,
                   payment_status.code AS payment_status,
                   payment.provider_status,
                   payment.provider_transaction_id,
                   payment.amount,
                   payment.currency,
                   payment.environment,
                   payment.expires_at,
                   payment.paid_at,
                   payment.requires_manual_refund
            FROM payments payment
            JOIN payment_statuses payment_status ON payment_status.id = payment.payment_status_id
            JOIN orders customer_order ON customer_order.id = payment.order_id
            JOIN states order_state ON order_state.id = customer_order.state_id
            """;

    /** Primer paso del bloqueo: solo la fila del pago, para que la lectura posterior vea el último estado confirmado. */
    static final String LOCK_PAYMENT_SQL = """
            SELECT payment.id
            FROM payments payment
            WHERE payment.provider = :provider
              AND payment.provider_reference = :reference
            FOR UPDATE
            """;

    /** Segundo paso: lectura fresca del pago ya bloqueado y bloqueo del pedido. */
    static final String LOCKED_PAYMENT_SQL = PAYMENT_COLUMNS + """
            WHERE payment.id = :paymentId
            FOR UPDATE OF customer_order
            """;

    static final String BUYER_PAYMENT_SQL = PAYMENT_COLUMNS + """
            WHERE payment.order_id = :orderId
              AND customer_order.person_id = :personId
              AND customer_order.deleted_at IS NULL
            ORDER BY payment.created_at DESC
            LIMIT 1
            """;

    static final String TRANSACTION_LINKED_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM payments
                WHERE provider = :provider
                  AND provider_transaction_id = :transactionId
                  AND id <> :paymentId
            )
            """;

    static final String APPROVE_PAYMENT_SQL = """
            UPDATE payments
            SET payment_status_id = (SELECT id FROM payment_statuses WHERE code = 'APPROVED'),
                provider_transaction_id = :transactionId,
                provider_status = :providerStatus,
                paid_at = now(),
                requires_manual_refund = :requiresManualRefund,
                updated_at = now()
            WHERE id = :paymentId
            """;

    static final String FAIL_PAYMENT_SQL = """
            UPDATE payments
            SET payment_status_id = (SELECT id FROM payment_statuses WHERE code = :statusCode),
                provider_transaction_id = :transactionId,
                provider_status = :providerStatus,
                updated_at = now()
            WHERE id = :paymentId
            """;

    static final String FLAG_MANUAL_REFUND_SQL = """
            UPDATE payments
            SET requires_manual_refund = true, updated_at = now()
            WHERE id = :paymentId
            """;

    static final String CONFIRM_ORDER_SQL = """
            UPDATE orders
            SET state_id = (SELECT id FROM states WHERE code = 'CONFIRMED'),
                updated_at = now()
            WHERE id = :orderId
              AND state_id = (SELECT id FROM states WHERE code = 'AWAITING_PAYMENT')
            """;

    static final String CANCEL_ORDER_SQL = """
            UPDATE orders
            SET state_id = (SELECT id FROM states WHERE code = 'CANCELLED'),
                cancellation_reason_code = :reasonCode,
                cancelled_at = now(),
                updated_at = now()
            WHERE id = :orderId
              AND state_id = (SELECT id FROM states WHERE code = 'AWAITING_PAYMENT')
            """;

    static final String CANCEL_SALES_SQL = """
            UPDATE sales
            SET state_id = (SELECT id FROM states WHERE code = 'CANCELLED'),
                updated_at = now()
            WHERE order_id = :orderId
              AND deleted_at IS NULL
              AND state_id = (SELECT id FROM states WHERE code = 'PENDING')
            """;

    /** Solo vence pagos que siguen abiertos: si otro proceso ya lo aprobó, no hace nada. */
    static final String EXPIRE_PAYMENT_SQL = """
            UPDATE payments
            SET payment_status_id = (SELECT id FROM payment_statuses WHERE code = 'EXPIRED'),
                updated_at = now()
            WHERE id = :paymentId
              AND payment_status_id IN (SELECT id FROM payment_statuses
                                        WHERE code IN ('PENDING', 'REJECTED', 'CANCELLED'))
            """;

    static final String INSERT_PAYMENT_EVENT_SQL = """
            INSERT INTO payment_events (payment_id, payment_status_id, message)
            VALUES (:paymentId, (SELECT id FROM payment_statuses WHERE code = :statusCode), :message)
            """;

    private static final String EXPIRED_CANDIDATES_SELECT = """
            SELECT payment.id AS payment_id, payment.order_id, payment.provider_reference, payment.environment,
                   payment.expires_at
            FROM payments payment
            JOIN payment_statuses payment_status ON payment_status.id = payment.payment_status_id
            WHERE payment.provider = :provider
              AND payment_status.code IN ('PENDING', 'REJECTED', 'CANCELLED')
              AND payment.expires_at <= now()
            """;

    private static final String EXPIRED_CANDIDATES_ORDER = """
            ORDER BY payment.expires_at, payment.id
            LIMIT :limit
            """;

    /** Primera tanda de pagos abiertos ya vencidos, sin bloqueo: se consultan en Wompi antes de vencerlos. */
    static final String EXPIRED_CANDIDATES_SQL = EXPIRED_CANDIDATES_SELECT + EXPIRED_CANDIDATES_ORDER;

    /**
     * Tandas siguientes, paginadas por {@code (expires_at, id)} tras el último candidato: los pagos que no pudieron
     * vencer (Wompi caído, aprobación sin aplicar) no tapan a los posteriores.
     */
    static final String EXPIRED_CANDIDATES_AFTER_SQL = EXPIRED_CANDIDATES_SELECT + """
              AND (payment.expires_at, payment.id) > (:afterExpiresAt, :afterPaymentId)
            """ + EXPIRED_CANDIDATES_ORDER;

    /**
     * Bloquea un pago si sigue abierto y vencido. {@code SKIP LOCKED}: una fila bloqueada la está aplicando un evento
     * de Wompi u otra instancia; se omite y se reintenta en la siguiente ronda. Si mientras tanto se aprobó, ya no
     * cumple la condición y no vence.
     */
    static final String LOCK_EXPIRED_SQL = """
            SELECT payment.id AS payment_id, payment.order_id
            FROM payments payment
            JOIN payment_statuses payment_status ON payment_status.id = payment.payment_status_id
            WHERE payment.id = :paymentId
              AND payment_status.code IN ('PENDING', 'REJECTED', 'CANCELLED')
              AND payment.expires_at <= now()
            FOR UPDATE OF payment SKIP LOCKED
            """;

    /**
     * Pagos abiertos, sin vencer y con más de {@code minAgeSeconds}, del entorno actual. Los vencidos que siguen
     * abiertos no entran: el vencimiento los consulta en Wompi justo antes de cerrarlos (un PSE aprobado tarde
     * confirma el pedido en vez de cancelarlo).
     */
    static final String LOCK_RECONCILIATION_SQL = """
            SELECT payment.provider_reference, payment.provider_transaction_id, payment.provider_status
            FROM payments payment
            JOIN payment_statuses payment_status ON payment_status.id = payment.payment_status_id
            WHERE payment.provider = :provider
              AND payment.environment = :environment
              AND payment_status.code IN ('PENDING', 'REJECTED', 'CANCELLED')
              AND payment.expires_at > now()
              AND payment.created_at <= now() - (:minAgeSeconds * interval '1 second')
            ORDER BY payment.created_at
            LIMIT :limit
            FOR UPDATE OF payment SKIP LOCKED
            """;

    static final String RESERVED_LINES_SQL = """
            SELECT detail.inventory_id, sum(detail.units) AS units
            FROM order_details detail
            WHERE detail.order_id = :orderId
              AND detail.deleted_at IS NULL
            GROUP BY detail.inventory_id
            """;

    private final JdbcClient jdbcClient;
    private final ApplicationEventPublisher events;

    @Autowired
    public OrderPayments(JdbcClient jdbcClient, ApplicationEventPublisher events) {
        this.jdbcClient = jdbcClient;
        this.events = events;
    }

    /** Sin publicador de eventos: no anuncia {@link OrderPaid} (pruebas y dobles). */
    public OrderPayments(JdbcClient jdbcClient) {
        this(jdbcClient, event -> {
        });
    }

    /**
     * Bloquea ({@code FOR UPDATE}) el pago del proveedor con esa referencia y luego su pedido, y devuelve el estado ya
     * confirmado de ambos. Vacío si no hay pago. Debe correr dentro de una transacción.
     */
    @Transactional
    public Optional<OrderPayment> lockByReference(String provider, String reference) {
        Optional<UUID> paymentId = jdbcClient.sql(LOCK_PAYMENT_SQL)
                .param("provider", provider)
                .param("reference", reference)
                .query((result, row) -> result.getObject("id", UUID.class))
                .list()
                .stream()
                .findFirst();
        if (paymentId.isEmpty()) {
            return Optional.empty();
        }
        return jdbcClient.sql(LOCKED_PAYMENT_SQL)
                .param("paymentId", paymentId.get())
                .query(OrderPayments::mapPayment)
                .list()
                .stream()
                .findFirst();
    }

    /** Pago más reciente del pedido si el pedido es de esa persona; ajeno o inexistente: vacío. */
    @Transactional(readOnly = true)
    public Optional<OrderPayment> findForBuyer(UUID personId, UUID orderId) {
        return jdbcClient.sql(BUYER_PAYMENT_SQL)
                .param("orderId", orderId)
                .param("personId", personId)
                .query(OrderPayments::mapPayment)
                .list()
                .stream()
                .findFirst();
    }

    /** {@code true} si otra fila de {@code payments} ya tiene esa transacción del proveedor. */
    @Transactional(readOnly = true)
    public boolean transactionLinkedToOtherPayment(String provider, String transactionId, UUID paymentId) {
        return Boolean.TRUE.equals(jdbcClient.sql(TRANSACTION_LINKED_SQL)
                .param("provider", provider)
                .param("transactionId", transactionId)
                .param("paymentId", paymentId)
                .query(Boolean.class)
                .single());
    }

    /** Pago APPROVED con {@code paid_at}, transacción y estado crudo; registra el evento. */
    @Transactional
    public void markApproved(UUID paymentId, String transactionId, String providerStatus,
            boolean requiresManualRefund, String message) {
        jdbcClient.sql(APPROVE_PAYMENT_SQL)
                .param("transactionId", transactionId)
                .param("providerStatus", providerStatus)
                .param("requiresManualRefund", requiresManualRefund)
                .param("paymentId", paymentId)
                .update();
        insertEvent(paymentId, "APPROVED", message);
    }

    /** Pago REJECTED o CANCELLED con la transacción y el estado crudo; registra el evento. */
    @Transactional
    public void markFailed(UUID paymentId, String statusCode, String transactionId, String providerStatus,
            String message) {
        jdbcClient.sql(FAIL_PAYMENT_SQL)
                .param("statusCode", statusCode)
                .param("transactionId", transactionId)
                .param("providerStatus", providerStatus)
                .param("paymentId", paymentId)
                .update();
        insertEvent(paymentId, statusCode, message);
    }

    @Transactional
    public void flagManualRefund(UUID paymentId) {
        jdbcClient.sql(FLAG_MANUAL_REFUND_SQL).param("paymentId", paymentId).update();
    }

    /** AWAITING_PAYMENT → CONFIRMED. {@code false} si el pedido ya no estaba en espera. */
    @Transactional
    public boolean confirmAwaitingOrder(UUID orderId) {
        boolean confirmed = jdbcClient.sql(CONFIRM_ORDER_SQL).param("orderId", orderId).update() == 1;
        if (confirmed) {
            events.publishEvent(new OrderPaid(orderId));
        }
        return confirmed;
    }

    /**
     * AWAITING_PAYMENT → CANCELLED con motivo y fecha, y sus ventas PENDING → CANCELLED. {@code false} (sin cambios) si
     * el pedido ya no estaba en espera.
     */
    @Transactional
    public boolean cancelAwaitingOrder(UUID orderId, String reasonCode) {
        int updated = jdbcClient.sql(CANCEL_ORDER_SQL)
                .param("reasonCode", reasonCode)
                .param("orderId", orderId)
                .update();
        if (updated != 1) {
            return false;
        }
        jdbcClient.sql(CANCEL_SALES_SQL).param("orderId", orderId).update();
        return true;
    }

    /** Pago abierto → EXPIRED; registra el evento. {@code false} si ya no estaba abierto. */
    @Transactional
    public boolean markExpired(UUID paymentId, String message) {
        boolean expired = jdbcClient.sql(EXPIRE_PAYMENT_SQL).param("paymentId", paymentId).update() == 1;
        if (expired) {
            insertEvent(paymentId, "EXPIRED", message);
        }
        return expired;
    }

    /**
     * Hasta {@code limit} pagos abiertos y vencidos, ordenados por {@code (expires_at, id)}, sin bloquearlos.
     * {@code after} {@code null} empieza por el principio; si no, sigue después de ese candidato.
     */
    @Transactional(readOnly = true)
    public List<ExpiredCandidate> findExpired(String provider, ExpiredCandidate after, int limit) {
        JdbcClient.StatementSpec statement = after == null
                ? jdbcClient.sql(EXPIRED_CANDIDATES_SQL)
                : jdbcClient.sql(EXPIRED_CANDIDATES_AFTER_SQL)
                        .param("afterExpiresAt", OffsetDateTime.ofInstant(after.expiresAt(), ZoneOffset.UTC))
                        .param("afterPaymentId", after.paymentId());
        return statement
                .param("provider", provider)
                .param("limit", limit)
                .query((result, row) -> {
                    OffsetDateTime expiresAt = result.getObject("expires_at", OffsetDateTime.class);
                    return new ExpiredCandidate(
                            result.getObject("payment_id", UUID.class),
                            result.getObject("order_id", UUID.class),
                            result.getString("provider_reference"),
                            result.getString("environment"),
                            expiresAt == null ? null : expiresAt.toInstant());
                })
                .list();
    }

    /** Bloquea el pago ({@code FOR UPDATE SKIP LOCKED}) si sigue abierto y vencido; si no, vacío. */
    @Transactional
    public Optional<ExpiredPayment> lockExpired(UUID paymentId) {
        return jdbcClient.sql(LOCK_EXPIRED_SQL)
                .param("paymentId", paymentId)
                .query((result, row) -> new ExpiredPayment(
                        result.getObject("payment_id", UUID.class),
                        result.getObject("order_id", UUID.class)))
                .list()
                .stream()
                .findFirst();
    }

    /** Pagos abiertos para conciliar ({@code FOR UPDATE SKIP LOCKED}); el bloqueo dura lo que la transacción. */
    @Transactional
    public List<ReconciliationCandidate> lockReconciliationCandidates(
            String provider, String environment, long minAgeSeconds, int limit) {
        return jdbcClient.sql(LOCK_RECONCILIATION_SQL)
                .param("provider", provider)
                .param("environment", environment)
                .param("minAgeSeconds", minAgeSeconds)
                .param("limit", limit)
                .query((result, row) -> new ReconciliationCandidate(
                        result.getString("provider_reference"),
                        result.getString("provider_transaction_id"),
                        result.getString("provider_status")))
                .list();
    }

    /** Unidades descontadas por el pedido, agrupadas por listado de inventario. */
    @Transactional(readOnly = true)
    public List<ReservedLine> reservedLines(UUID orderId) {
        return jdbcClient.sql(RESERVED_LINES_SQL)
                .param("orderId", orderId)
                .query((result, row) -> new ReservedLine(
                        result.getObject("inventory_id", UUID.class),
                        result.getInt("units")))
                .list();
    }

    private void insertEvent(UUID paymentId, String statusCode, String message) {
        jdbcClient.sql(INSERT_PAYMENT_EVENT_SQL)
                .param("paymentId", paymentId)
                .param("statusCode", statusCode)
                .param("message", message)
                .update();
    }

    private static OrderPayment mapPayment(ResultSet result, int row) throws SQLException {
        OffsetDateTime expiresAt = result.getObject("expires_at", OffsetDateTime.class);
        Timestamp paidAt = result.getTimestamp("paid_at");
        return new OrderPayment(
                result.getObject("payment_id", UUID.class),
                result.getObject("order_id", UUID.class),
                result.getObject("person_id", UUID.class),
                result.getLong("order_number"),
                result.getString("order_status"),
                result.getString("provider"),
                result.getString("provider_reference"),
                result.getString("payment_status"),
                result.getString("provider_status"),
                result.getString("provider_transaction_id"),
                result.getBigDecimal("amount"),
                result.getString("currency"),
                result.getString("environment"),
                expiresAt == null ? null : expiresAt.toInstant(),
                paidAt == null ? null : paidAt.toLocalDateTime(),
                result.getBoolean("requires_manual_refund"));
    }

    /** Pago vencido bloqueado y su pedido. */
    public record ExpiredPayment(UUID paymentId, UUID orderId) {
    }

    /** Pago abierto y vencido, aún sin bloquear: referencia y entorno para consultarlo en el proveedor. */
    public record ExpiredCandidate(UUID paymentId, UUID orderId, String reference, String environment,
            java.time.Instant expiresAt) {
    }

    /** Referencia abierta y lo último que se aplicó de ella (para no registrar la misma transacción otra vez). */
    public record ReconciliationCandidate(String reference, String providerTransactionId, String providerStatus) {
    }

    /** Unidades descontadas de un listado de inventario. */
    public record ReservedLine(UUID inventoryId, int units) {
    }
}
