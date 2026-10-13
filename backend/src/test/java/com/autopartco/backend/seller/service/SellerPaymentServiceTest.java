package com.autopartco.backend.seller.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.billing.internal.wompi.WompiProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.seller.repository.SellerPaymentRepository;
import com.autopartco.backend.seller.repository.SellerPlanRepository;

@ExtendWith(MockitoExtension.class)
class SellerPaymentServiceTest {
    private static final String EVENTS_SECRET = "test-events-secret";

    @Mock
    private SellerPaymentRepository paymentRepository;

    @Mock
    private StoreApplicationRepository applicationRepository;

    @Mock
    private SellerPlanRepository planRepository;

    @Mock
    private AccountLookup accountLookup;

    private SellerPaymentService paymentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsWebhookWithInvalidChecksumBeforeLookingUpPayment() throws Exception {
        paymentService = new SellerPaymentService(
                paymentRepository,
                applicationRepository,
                planRepository,
                new WompiProperties("", "", EVENTS_SECRET, "", ""),
                accountLookup
        );

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
        verify(paymentRepository, never()).findByReferenceForUpdate("APC-1");
    }

    @Test
    void acceptsDynamicallySpecifiedChecksumProperties() throws Exception {
        paymentService = new SellerPaymentService(
                paymentRepository,
                applicationRepository,
                planRepository,
                new WompiProperties("", "", EVENTS_SECRET, "", ""),
                accountLookup
        );
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
        verify(paymentRepository).findByReferenceForUpdate("APC-1");
    }
}
