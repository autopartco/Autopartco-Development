package com.autopartco.backend.core.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.payments.PaymentSyncRateLimiter;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estado de un pago de suscripción para la página de resultado del portal y su sincronización con Wompi tras la
 * redirección (contrato §5). Solo el OWNER. La redirección nunca es prueba de pago: se consulta la transacción con la
 * llave privada y se aplica con {@link WompiEventSource#REDIRECT_SYNC} solo si su referencia es la del pago.
 * <p>
 * Sin {@code @Transactional}: la llamada a Wompi va fuera de cualquier transacción.
 */
@Service
@AllArgsConstructor
@Slf4j
public class SubscriptionPaymentStatus {
    private final StoreAccess storeAccess;
    private final Subscriptions subscriptions;
    private final WompiClient wompiClient;
    private final WompiEventRouter eventRouter;
    private final PaymentSyncRateLimiter rateLimiter;

    public SubscriptionPaymentView get(UUID storeId, String email, String reference) {
        storeAccess.requireOwner(email, storeId);
        return subscriptions.paymentView(storeId, reference);
    }

    public SubscriptionPaymentView sync(UUID storeId, String email, String reference, String transactionId) {
        storeAccess.requireOwner(email, storeId);
        rateLimiter.acquire(email);
        SubscriptionPaymentView current = subscriptions.paymentView(storeId, reference);
        if (!"WOMPI".equals(current.provider()) || "APPROVED".equals(current.status())) {
            return current;
        }
        Optional<WompiTransaction> transaction;
        try {
            transaction = wompiClient.findTransaction(transactionId);
        } catch (WompiUnavailableException exception) {
            log.warn("Wompi lookup failed while syncing subscription payment {}: {}",
                    reference, exception.getMessage());
            return current;
        }
        if (transaction.isEmpty()) {
            throw new NotFoundException("PAYMENT_TRANSACTION_NOT_FOUND", "Wompi no reconoce esa transacción.");
        }
        if (!reference.equals(transaction.get().reference())) {
            throw new ConflictException(
                    "PAYMENT_REFERENCE_MISMATCH",
                    "La transacción no corresponde a este pago."
            );
        }
        eventRouter.apply(transaction.get(), WompiEventSource.REDIRECT_SYNC);
        return subscriptions.paymentView(storeId, reference);
    }
}
