package com.autopartco.backend.marketplace.checkout;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.PaymentSyncRateLimiter;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Estado del pago de un pedido para su dueño y sincronización con Wompi tras la redirección (contrato §3 y §4). Un
 * pedido ajeno responde igual que uno inexistente ({@code ORDER_NOT_FOUND}). La redirección nunca es prueba de pago:
 * se consulta la transacción con la llave privada y solo se aplica ({@link WompiEventSource#REDIRECT_SYNC}) si su
 * referencia es la del pedido.
 * <p>
 * Sin {@code @Transactional}: la llamada a Wompi va fuera de cualquier transacción.
 */
@Slf4j
@Service
public class OrderPaymentStatus {
    private final AccountLookup accountLookup;
    private final OrderPayments orderPayments;
    private final MarketplacePaymentLinks links;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final PaymentSyncRateLimiter rateLimiter;
    private final Clock clock;

    @Autowired
    public OrderPaymentStatus(
            AccountLookup accountLookup,
            OrderPayments orderPayments,
            MarketplacePaymentLinks links,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSyncRateLimiter rateLimiter
    ) {
        this(accountLookup, orderPayments, links, wompiClient, eventRouter, rateLimiter, Clock.systemUTC());
    }

    OrderPaymentStatus(
            AccountLookup accountLookup,
            OrderPayments orderPayments,
            MarketplacePaymentLinks links,
            WompiClient wompiClient,
            WompiEventRouter eventRouter,
            PaymentSyncRateLimiter rateLimiter,
            Clock clock
    ) {
        this.accountLookup = accountLookup;
        this.orderPayments = orderPayments;
        this.links = links;
        this.wompiClient = wompiClient;
        this.eventRouter = eventRouter;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public OrderPaymentView get(String email, UUID orderId) {
        return view(load(personId(email), orderId));
    }

    public OrderPaymentView sync(String email, UUID orderId, String transactionId) {
        rateLimiter.acquire(email);
        UUID personId = personId(email);
        OrderPayment current = load(personId, orderId);
        if (!MarketplacePaymentLinks.PROVIDER.equals(current.provider()) || "APPROVED".equals(current.status())) {
            return view(current);
        }
        Optional<WompiTransaction> transaction;
        try {
            transaction = wompiClient.findTransaction(transactionId);
        } catch (WompiUnavailableException exception) {
            log.warn("Wompi lookup failed while syncing order payment {}: {}",
                    current.reference(), exception.getMessage());
            return view(current);
        }
        if (transaction.isEmpty()) {
            throw new NotFoundException("PAYMENT_TRANSACTION_NOT_FOUND", "Wompi no reconoce esa transacción.");
        }
        if (!current.reference().equals(transaction.get().reference())) {
            throw new ConflictException(
                    "PAYMENT_REFERENCE_MISMATCH",
                    "La transacción no corresponde a este pedido."
            );
        }
        eventRouter.apply(transaction.get(), WompiEventSource.REDIRECT_SYNC);
        return view(load(personId, orderId));
    }

    private UUID personId(String email) {
        return accountLookup.requireByEmail(email).personId();
    }

    private OrderPayment load(UUID personId, UUID orderId) {
        return orderPayments.findForBuyer(personId, orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido."));
    }

    private OrderPaymentView view(OrderPayment payment) {
        return new OrderPaymentView(
                payment.orderId(),
                payment.reference(),
                payment.status(),
                payment.providerStatus(),
                payment.orderStatus(),
                MarketplacePaymentLinks.amountInCents(payment.amount()),
                payment.currency(),
                links.checkoutUrlIfPayable(payment, clock.instant()),
                MarketplacePaymentLinks.colombiaTime(payment.expiresAt()),
                payment.paidAt()
        );
    }
}
