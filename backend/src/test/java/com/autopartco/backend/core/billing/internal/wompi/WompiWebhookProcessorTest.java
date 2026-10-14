package com.autopartco.backend.core.billing.internal.wompi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.billing.internal.Plan;
import com.autopartco.backend.core.billing.internal.StoreSubscription;
import com.autopartco.backend.core.billing.internal.StoreSubscriptionRepository;
import com.autopartco.backend.core.billing.internal.SubscriptionPayment;
import com.autopartco.backend.core.billing.internal.SubscriptionPaymentRepository;

@ExtendWith(MockitoExtension.class)
class WompiWebhookProcessorTest {
    private static final String EVENTS_SECRET = "test-events-secret";

    @Mock
    private SubscriptionPaymentRepository paymentRepository;

    @Mock
    private StoreSubscriptionRepository applicationRepository;

    private WompiWebhookProcessor paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WompiWebhookProcessor processor() {
        return new WompiWebhookProcessor(
                paymentRepository,
                applicationRepository,
                new WompiSignatureVerifier(new WompiProperties("", "", EVENTS_SECRET, "", ""))
        );
    }

    @Test
    void rejectsWebhookWithInvalidChecksumBeforeLookingUpPayment() throws Exception {
        paymentService = processor();

        var payload = objectMapper.readTree("""
                {
                  "data": {"transaction": {
                    "id": "transaction-1",
                    "reference": "APC-1",
                    "status": "APPROVED",
                    "amount_in_cents": 10000,
                    "currency": "COP"
                  }},
                  "timestamp": 123,
                  "signature": {
                    "properties": ["data.transaction.id"],
                    "checksum": "invalid"
                  }
                }
                """);

        assertThrows(BadRequestException.class, () -> paymentService.processWebhook(payload));
        verify(paymentRepository, never()).lockByReference("APC-1");
    }

    @Test
    void acceptsDynamicallySpecifiedChecksumProperties() throws Exception {
        paymentService = processor();
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                "transaction-1123test-events-secret".getBytes(StandardCharsets.UTF_8)
        ));
        var payload = objectMapper.readTree("""
                {
                  "data": {"transaction": {
                    "id": "transaction-1",
                    "reference": "APC-1",
                    "status": "APPROVED",
                    "amount_in_cents": 10000,
                    "currency": "COP"
                  }},
                  "timestamp": 123,
                  "signature": {
                    "properties": ["data.transaction.id"],
                    "checksum": "%s"
                  }
                }
                """.formatted(checksum));

        assertThrows(NotFoundException.class, () -> paymentService.processWebhook(payload));
        verify(paymentRepository).lockByReference("APC-1");
    }

    @Test
    void approvedPaymentExtendsPaidThroughFromTrialEndAndSetsPlan() throws Exception {
        paymentService = processor();
        UUID applicationId = UUID.randomUUID();
        Plan paidPlan = Plan.builder().code("BUSINESS").build();
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .storeApplicationId(applicationId)
                .plan(paidPlan)
                .reference("APC-1")
                .amountInCents(10000)
                .prepaidMonths(3)
                .currency("COP")
                .status("PENDING")
                .build();
        LocalDateTime trialEndsAt = LocalDateTime.now().plusDays(10);
        StoreSubscription subscription = StoreSubscription.builder()
                .id(applicationId)
                .trialEndsAt(trialEndsAt)
                .build();
        when(paymentRepository.lockByReference("APC-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.existsByProviderTransactionId("transaction-1")).thenReturn(false);
        when(applicationRepository.lockById(applicationId)).thenReturn(Optional.of(subscription));

        paymentService.processWebhook(signedEvent("APPROVED", 10000));

        assertEquals("APPROVED", payment.getStatus());
        assertEquals("transaction-1", payment.getProviderTransactionId());
        assertEquals(paidPlan, subscription.getPlan());
        assertEquals(trialEndsAt.plusMonths(3), subscription.getPaidThrough());
    }

    @Test
    void replayOfAlreadyApprovedTransactionIsIgnored() throws Exception {
        paymentService = processor();
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .storeApplicationId(UUID.randomUUID())
                .reference("APC-1")
                .amountInCents(10000)
                .prepaidMonths(1)
                .currency("COP")
                .status("APPROVED")
                .providerTransactionId("transaction-1")
                .build();
        when(paymentRepository.lockByReference("APC-1")).thenReturn(Optional.of(payment));

        paymentService.processWebhook(signedEvent("APPROVED", 10000));

        verify(applicationRepository, never()).lockById(payment.getStoreApplicationId());
    }

    @Test
    void rejectsAmountMismatch() throws Exception {
        paymentService = processor();
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .storeApplicationId(UUID.randomUUID())
                .reference("APC-1")
                .amountInCents(20000)
                .prepaidMonths(1)
                .currency("COP")
                .status("PENDING")
                .build();
        when(paymentRepository.lockByReference("APC-1")).thenReturn(Optional.of(payment));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> paymentService.processWebhook(signedEvent("APPROVED", 10000))
        );
        assertEquals("WOMPI_PAYMENT_MISMATCH", error.getCode());
        assertEquals("PENDING", payment.getStatus());
    }

    private JsonNode signedEvent(String status, long amountInCents) throws Exception {
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                ("transaction-1" + status + amountInCents + "123" + EVENTS_SECRET).getBytes(StandardCharsets.UTF_8)
        ));
        return objectMapper.readTree("""
                {
                  "data": {"transaction": {
                    "id": "transaction-1",
                    "reference": "APC-1",
                    "status": "%s",
                    "amount_in_cents": %d,
                    "currency": "COP"
                  }},
                  "timestamp": 123,
                  "signature": {
                    "properties": ["data.transaction.id", "data.transaction.status", "data.transaction.amount_in_cents"],
                    "checksum": "%s"
                  }
                }
                """.formatted(status, amountInCents, checksum));
    }
}
