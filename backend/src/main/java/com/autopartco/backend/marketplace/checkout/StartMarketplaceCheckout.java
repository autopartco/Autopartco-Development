package com.autopartco.backend.marketplace.checkout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.NewOrder;
import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.order.PaymentReferenceOwner;
import com.autopartco.backend.core.order.PendingPayment;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Inicio del pago con Wompi (ADR 0004 §2.4, solo con {@code APP_CHECKOUT_PAYMENT_PROVIDER=WOMPI}). En una
 * transacción y sin llamadas de red: valida el carrito, descuenta el stock como reserva y crea el pedido
 * AWAITING_PAYMENT, sus ventas PENDING y el pago PENDING ({@code APM-…}, {@code idempotency_key = clientRequestId},
 * entorno y {@code expires_at}). Devuelve el enlace firmado del Web Checkout. El monto lo calcula solo el servidor.
 */
@Slf4j
@Service
public class StartMarketplaceCheckout {
    static final String WOMPI_METHOD = "WOMPI";
    static final String REFERENCE_PREFIX = "APM-";

    private final AccountLookup accountLookup;
    private final OrderPlacement orderPlacement;
    private final OrderPayments orderPayments;
    private final PlaceMarketplaceOrder placeMarketplaceOrder;
    private final MarketplacePaymentLinks links;
    private final PaymentSettings paymentSettings;
    private final Clock clock;

    @Autowired
    public StartMarketplaceCheckout(
            AccountLookup accountLookup,
            OrderPlacement orderPlacement,
            OrderPayments orderPayments,
            PlaceMarketplaceOrder placeMarketplaceOrder,
            MarketplacePaymentLinks links,
            PaymentSettings paymentSettings
    ) {
        this(accountLookup, orderPlacement, orderPayments, placeMarketplaceOrder, links, paymentSettings,
                Clock.systemUTC());
    }

    StartMarketplaceCheckout(
            AccountLookup accountLookup,
            OrderPlacement orderPlacement,
            OrderPayments orderPayments,
            PlaceMarketplaceOrder placeMarketplaceOrder,
            MarketplacePaymentLinks links,
            PaymentSettings paymentSettings,
            Clock clock
    ) {
        this.accountLookup = accountLookup;
        this.orderPlacement = orderPlacement;
        this.orderPayments = orderPayments;
        this.placeMarketplaceOrder = placeMarketplaceOrder;
        this.links = links;
        this.paymentSettings = paymentSettings;
        this.clock = clock;
    }

    @Transactional
    public WompiCheckoutResult execute(String email, PlaceOrderRequest request) {
        // 1. Comprador autenticado.
        UUID personId = accountLookup.requireByEmail(email).personId();

        // 2. Sin pares producto-sucursal repetidos.
        PlaceMarketplaceOrder.rejectDuplicateLines(request.items());

        // 3. Método WOMPI activo; sin resultado simulado.
        String paymentMethod = request.paymentMethod();
        if (!WOMPI_METHOD.equals(paymentMethod) || !orderPlacement.isActivePaymentMethod(paymentMethod)) {
            throw PlaceMarketplaceOrder.paymentMethodNotSupported();
        }
        if (request.simulatedOutcome() != null) {
            throw new BadRequestException(
                    "SIMULATED_OUTCOME_NOT_ALLOWED",
                    "El resultado simulado no se admite con el proveedor de pagos actual."
            );
        }

        // 4. Idempotencia por clientRequestId (payments.idempotency_key).
        String idempotencyKey = request.clientRequestId().toString();
        Optional<PaymentReferenceOwner> existing = orderPlacement.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PaymentReferenceOwner owner = existing.get();
            if (personId.equals(owner.personId())) {
                return new WompiCheckoutResult(currentState(personId, owner.orderId()), true);
            }
            throw PlaceMarketplaceOrder.duplicateRequest();
        }

        // 5-9. Dirección, listados, descuento de stock (reserva) y ventas con precios del servidor.
        PreparedOrder prepared = placeMarketplaceOrder.prepare(personId, request);
        long amountInCents = MarketplacePaymentLinks.amountInCents(prepared.total());
        if (amountInCents <= 0) {
            throw new IllegalStateException("Checkout total must be positive");
        }

        // 10. Pedido en espera y pago pendiente; una clave repetida en carrera es un 409.
        String reference = REFERENCE_PREFIX + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
                .plus(Duration.ofMinutes(paymentSettings.reservationMinutes()));
        PendingPayment payment = new PendingPayment(
                MarketplacePaymentLinks.PROVIDER,
                reference,
                idempotencyKey,
                paymentSettings.wompiEnvironment().name(),
                expiresAt
        );
        NewOrder newOrder = new NewOrder(personId, prepared.address(), paymentMethod, reference, prepared.sales(),
                request.anonymousId());
        UUID orderId;
        try {
            orderId = orderPlacement.placeAwaitingPayment(newOrder, payment);
        } catch (DuplicateKeyException exception) {
            throw PlaceMarketplaceOrder.duplicateRequest();
        }
        log.info("Pedido en espera de pago Wompi creado: pedido={}, referencia={}, centavos={}",
                orderId, reference, amountInCents);

        // 11. Enlace firmado (sin red).
        return new WompiCheckoutResult(currentState(personId, orderId), false);
    }

    private WompiCheckoutResponse currentState(UUID personId, UUID orderId) {
        OrderPayment payment = orderPayments.findForBuyer(personId, orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido."));
        return new WompiCheckoutResponse(
                payment.orderId(),
                payment.orderNumber(),
                payment.orderStatus(),
                new WompiCheckoutResponse.Payment(
                        payment.reference(),
                        MarketplacePaymentLinks.amountInCents(payment.amount()),
                        payment.currency(),
                        links.checkoutUrlIfPayable(payment, clock.instant()),
                        MarketplacePaymentLinks.colombiaTime(payment.expiresAt())
                )
        );
    }
}
