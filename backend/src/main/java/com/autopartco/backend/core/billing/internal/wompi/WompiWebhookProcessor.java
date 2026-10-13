package com.autopartco.backend.core.billing.internal.wompi;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;

import lombok.AllArgsConstructor;

/**
 * Procesa los eventos de transacción de Wompi: verifica la firma, cuadra monto y moneda, aplica el pago de forma
 * idempotente y extiende la vigencia pagada de la suscripción.
 */
@Service
@AllArgsConstructor
public class WompiWebhookProcessor {
    private final SubscriptionPaymentRepository paymentRepository;
    private final StoreSubscriptionRepository applicationRepository;
    private final WompiSignatureVerifier signatureVerifier;

    @Transactional
    public void processWebhook(JsonNode payload) {
        signatureVerifier.verifyWebhookSignature(payload);
        JsonNode transaction = payload.path("data").path("transaction");
        String reference = requiredText(transaction, "reference");
        String providerId = requiredText(transaction, "id");
        String status = requiredText(transaction, "status");
        SubscriptionPayment payment = paymentRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_PAYMENT_NOT_FOUND",
                        "No se encontró el pago asociado a la referencia."
                ));

        long amount;
        JsonNode amountNode = transaction.path("amount_in_cents");
        if (!amountNode.isIntegralNumber() || !amountNode.canConvertToLong()) {
            throw new BadRequestException("WOMPI_AMOUNT_INVALID", "El valor recibido por Wompi no es válido.");
        }
        try {
            amount = amountNode.longValue();
        } catch (ArithmeticException exception) {
            throw new BadRequestException("WOMPI_AMOUNT_INVALID", "El valor recibido por Wompi no es válido.");
        }
        if (amount != payment.getAmountInCents()
                || !payment.getCurrency().equals(transaction.path("currency").asText())) {
            throw new BadRequestException("WOMPI_PAYMENT_MISMATCH", "El monto o la moneda del pago no coincide.");
        }
        if ("APPROVED".equals(status)) {
            if ("APPROVED".equals(payment.getStatus())) {
                if (providerId.equals(payment.getProviderTransactionId())) {
                    return;
                }
                throw new ConflictException(
                        "SELLER_PAYMENT_ALREADY_SETTLED",
                        "El pago ya fue aplicado a esta solicitud."
                );
            }
            if (!providerId.equals(payment.getProviderTransactionId())
                    && paymentRepository.existsByProviderTransactionId(providerId)) {
                throw new ConflictException(
                        "WOMPI_TRANSACTION_ALREADY_PROCESSED",
                        "La transacción ya fue asociada a otro pago."
                );
            }
            payment.setStatus("APPROVED");
            payment.setProviderTransactionId(providerId);
            StoreSubscription application = applicationRepository.lockById(payment.getStoreApplicationId())
                    .orElseThrow(() -> new NotFoundException(
                            "SELLER_APPLICATION_NOT_FOUND",
                            "No se encontró la solicitud de vendedor del pago."
                    ));
            application.setPlan(payment.getPlan());
            LocalDateTime start = LocalDateTime.now();
            if (application.getTrialEndsAt() != null && application.getTrialEndsAt().isAfter(start)) {
                start = application.getTrialEndsAt();
            }
            if (application.getPaidThrough() != null && application.getPaidThrough().isAfter(start)) {
                start = application.getPaidThrough();
            }
            application.setPaidThrough(start.plusMonths(payment.getPrepaidMonths()));
        } else if (List.of("DECLINED", "ERROR", "VOIDED", "FAILED").contains(status)) {
            if (!"APPROVED".equals(payment.getStatus())) {
                payment.setStatus(status);
                payment.setProviderTransactionId(providerId);
            }
        } else if (!"PENDING".equals(status)) {
            throw new BadRequestException("WOMPI_STATUS_UNSUPPORTED", "El estado recibido por Wompi no es compatible.");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new BadRequestException("WOMPI_PAYLOAD_INVALID", "Falta un dato requerido en el evento.");
        }
        return value;
    }
}
