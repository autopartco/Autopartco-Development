package com.autopartco.backend.seller.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.seller.dto.SellerCheckoutRequest;
import com.autopartco.backend.seller.dto.SellerCheckoutResponse;
import com.autopartco.backend.seller.entity.SellerApplication;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.entity.SellerPayment;
import com.autopartco.backend.seller.entity.SellerPlan;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;
import com.autopartco.backend.seller.repository.SellerPaymentRepository;
import com.autopartco.backend.seller.repository.SellerPlanRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SellerPaymentService {
    private final SellerPaymentRepository paymentRepository;
    private final SellerApplicationRepository applicationRepository;
    private final SellerPlanRepository planRepository;
    private final WompiProperties wompiProperties;

    @Transactional
    public SellerCheckoutResponse createCheckout(
            UUID applicationId,
            String email,
            SellerCheckoutRequest request
    ) {
        SellerApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la solicitud de vendedor."
                ));
        if (!application.getApplicant().getEmail().equalsIgnoreCase(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "La solicitud no pertenece a la cuenta autenticada."
            );
        }
        if (application.getStatus() != SellerApplicationStatus.ACTIVE) {
            throw new ConflictException("SELLER_APPLICATION_NOT_ACTIVE", "El almacén todavía no está habilitado.");
        }
        SellerPlan plan = planRepository.findById(request.planId())
                .filter(SellerPlan::isEnabled)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_PLAN_NOT_FOUND",
                        "El plan seleccionado no existe o no está disponible."
                ));
        if (plan.getMonthlyPriceCop() <= 0 || "TRY".equalsIgnoreCase(plan.getCode())) {
            throw new BadRequestException("SELLER_PLAN_NOT_PAYABLE", "El plan gratuito no requiere pago.");
        }
        if (request.months() < 1 || request.months() > 12) {
            throw new BadRequestException("INVALID_PREPAID_MONTHS", "Puedes pagar entre 1 y 12 meses.");
        }
        requireWompiConfiguration();

        long amountInCents;
        try {
            amountInCents = Math.multiplyExact(
                    Math.multiplyExact(plan.getMonthlyPriceCop(), request.months()),
                    100L
            );
        } catch (ArithmeticException exception) {
            throw new BadRequestException("SELLER_PAYMENT_AMOUNT_INVALID", "El valor del pago supera el límite permitido.");
        }

        String reference = "APC-" + UUID.randomUUID().toString().replace("-", "");
        String currency = plan.getCurrency();
        SellerPayment payment = paymentRepository.save(SellerPayment.builder()
                .application(application)
                .plan(plan)
                .reference(reference)
                .amountInCents(amountInCents)
                .prepaidMonths(request.months())
                .currency(currency)
                .status("PENDING")
                .build());

        String integritySignature = sha256(
                reference + amountInCents + currency + wompiProperties.integritySecret()
        );
        String checkoutUrl = UriComponentsBuilder.fromUriString(wompiProperties.checkoutUrl())
                .queryParam("public-key", wompiProperties.publicKey())
                .queryParam("currency", currency)
                .queryParam("amount-in-cents", amountInCents)
                .queryParam("reference", reference)
                .queryParam("signature:integrity", integritySignature)
                .queryParam("redirect-url", wompiProperties.redirectUrl())
                .build()
                .encode()
                .toUriString();
        return new SellerCheckoutResponse(
                payment.getReference(),
                checkoutUrl,
                amountInCents,
                currency,
                request.months()
        );
    }

    @Transactional
    public void processWebhook(JsonNode payload) {
        verifyWebhookSignature(payload);
        JsonNode transaction = payload.path("data").path("transaction");
        String reference = requiredText(transaction, "reference");
        String providerId = requiredText(transaction, "id");
        String status = requiredText(transaction, "status");
        SellerPayment payment = paymentRepository.findByReferenceForUpdate(reference)
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
            SellerApplication application = applicationRepository.findByIdForUpdate(payment.getApplication().getId())
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

    private void verifyWebhookSignature(JsonNode payload) {
        if (wompiProperties.eventsSecret() == null || wompiProperties.eventsSecret().isBlank()) {
            throw new IllegalStateException("Wompi event-signature secret is not configured.");
        }
        JsonNode signature = payload.path("signature");
        JsonNode properties = signature.path("properties");
        String checksum = signature.path("checksum").asText("");
        JsonNode timestamp = payload.get("timestamp");
        if (!properties.isArray() || properties.isEmpty() || checksum.isBlank() || timestamp == null) {
            throw new BadRequestException("WOMPI_SIGNATURE_MISSING", "La firma del evento está incompleta.");
        }
        StringBuilder signedValues = new StringBuilder();
        for (JsonNode property : properties) {
            if (!property.isTextual()) {
                throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "Las propiedades de firma no son válidas.");
            }
            JsonNode value = readDottedPath(payload, property.asText());
            if (value == null || value.isMissingNode() || value.isNull()) {
                throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "Falta un campo requerido para verificar la firma.");
            }
            signedValues.append(value.isValueNode() ? value.asText() : value.toString());
        }
        signedValues.append(timestamp.isValueNode() ? timestamp.asText() : timestamp.toString())
                .append(wompiProperties.eventsSecret());
        byte[] expected = sha256(signedValues.toString()).getBytes(StandardCharsets.US_ASCII);
        byte[] received = checksum.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, received)) {
            throw new BadRequestException("WOMPI_SIGNATURE_INVALID", "La firma del evento no es válida.");
        }
    }

    private JsonNode readDottedPath(JsonNode root, String dottedPath) {
        JsonNode value = root;
        for (String component : dottedPath.split("\\.")) {
            value = value.path(component);
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new BadRequestException("WOMPI_PAYLOAD_INVALID", "Falta un dato requerido en el evento.");
        }
        return value;
    }

    private void requireWompiConfiguration() {
        if (isBlank(wompiProperties.publicKey())
                || isBlank(wompiProperties.integritySecret())
                || isBlank(wompiProperties.checkoutUrl())
                || isBlank(wompiProperties.redirectUrl())) {
            throw new IllegalStateException("Wompi checkout configuration is incomplete.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
