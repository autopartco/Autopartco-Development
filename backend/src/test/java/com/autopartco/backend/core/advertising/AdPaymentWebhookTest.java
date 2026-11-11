package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.core.advertising.internal.AdPayment;
import com.autopartco.backend.core.advertising.internal.AdPaymentRepository;
import com.autopartco.backend.core.advertising.internal.AdWompiHandler;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;

class AdPaymentWebhookTest {
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final long AMOUNT_CENTS = 15_000_000L;

    private final AdPaymentRepository paymentRepository = mock(AdPaymentRepository.class);
    private final AdCampaignRepository campaignRepository = mock(AdCampaignRepository.class);
    private final AdCandidateCache cache = mock(AdCandidateCache.class);
    private final WompiProperties properties = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_x",
            "prv_test_x", "integrity", "events", "https://checkout.wompi.co/p/", "http://localhost/r");
    private final AdWompiHandler handler = new AdWompiHandler(paymentRepository, campaignRepository, properties,
            cache, Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<AdPayment> payments = new ArrayList<>();

    private AdCampaign campaign;
    private AdPayment payment;

    @BeforeEach
    void setUp() {
        campaign = campaign(AdCampaignStatus.PENDING_PAYMENT, TODAY, TODAY.plusDays(9));
        payment = payment(campaign, "ADS-1");
        when(paymentRepository.findByCampaignIdAndStatus(any(), anyString())).thenAnswer(invocation ->
                payments.stream()
                        .filter(found -> found.getCampaignId().equals(invocation.getArgument(0))
                                && found.getStatus().equals(invocation.getArgument(1)))
                        .toList());
        when(paymentRepository.existsByProviderTransactionId(anyString())).thenAnswer(invocation ->
                payments.stream().anyMatch(found -> invocation.getArgument(0).equals(found.getProviderTransactionId())));
    }

    private AdCampaign campaign(AdCampaignStatus status, LocalDate start, LocalDate end) {
        AdCampaign created = AdCampaign.builder()
                .id(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .name("c")
                .status(status)
                .startsOn(start)
                .endsOn(end)
                .totalPrice(new BigDecimal("150000.00"))
                .createdBy(UUID.randomUUID())
                .productIds(new LinkedHashSet<>(List.of(UUID.randomUUID())))
                .build();
        when(campaignRepository.lockById(created.getId())).thenReturn(Optional.of(created));
        return created;
    }

    private AdPayment payment(AdCampaign owner, String reference) {
        AdPayment created = AdPayment.builder()
                .id(UUID.randomUUID())
                .campaignId(owner.getId())
                .reference(reference)
                .amountInCents(AMOUNT_CENTS)
                .currency("COP")
                .status(AdPayment.PENDING)
                .provider(AdPayment.WOMPI)
                .environment("SANDBOX")
                .build();
        payments.add(created);
        when(paymentRepository.lockByReference(reference)).thenReturn(Optional.of(created));
        return created;
    }

    private static WompiTransaction transaction(String id, String reference, String status, long cents) {
        return new WompiTransaction(id, reference, status, cents, "COP", null);
    }

    @Test
    void approvedPaymentActivatesTheCampaignOnlyOnceEvenIfRepeated() {
        WompiTransaction approved = transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS);

        assertThat(handler.apply(approved, WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.APPLIED);
        LocalDateTime paidAt = campaign.getPaidAt();
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(paidAt).isEqualTo(LocalDateTime.of(2026, 11, 11, 10, 0));
        assertThat(payment.getStatus()).isEqualTo(AdPayment.APPROVED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("tx-1");

        // El mismo evento otra vez (webhook repetido o sync tras la redirección): sin efectos.
        assertThat(handler.apply(approved, WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.IGNORED);
        assertThat(handler.apply(approved, WompiEventSource.REDIRECT_SYNC)).isEqualTo(WompiEventOutcome.IGNORED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(campaign.getPaidAt()).isEqualTo(paidAt);
        assertThat(campaign.isRequiresManualRefund()).isFalse();
        verify(campaignRepository, times(1)).lockById(campaign.getId());
    }

    @Test
    void approvedBeforeStartIsScheduled() {
        campaign.setStartsOn(TODAY.plusDays(3));

        handler.apply(transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS), WompiEventSource.WEBHOOK);

        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.SCHEDULED);
        assertThat(campaign.getPaidAt()).isNotNull();
    }

    @Test
    void amountMismatchIsRejectedWithoutChanges() {
        WompiEventOutcome outcome = handler.apply(
                transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS - 100), WompiEventSource.WEBHOOK);

        assertThat(outcome).isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PENDING_PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(AdPayment.PENDING);
    }

    @Test
    void unknownReferenceIsReported() {
        when(paymentRepository.lockByReference("ADS-404")).thenReturn(Optional.empty());

        assertThat(handler.apply(transaction("tx-9", "ADS-404", WompiTransaction.APPROVED, AMOUNT_CENTS),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.UNKNOWN_REFERENCE);
        assertThat(handler.supports("ADS-1")).isTrue();
        assertThat(handler.supports("APC-1")).isFalse();
    }

    @Test
    void declinedPaymentReturnsTheCampaignToDraft() {
        WompiTransaction declined = transaction("tx-2", "ADS-1", WompiTransaction.DECLINED, AMOUNT_CENTS);

        assertThat(handler.apply(declined, WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.APPLIED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.DRAFT);
        assertThat(payment.getStatus()).isEqualTo("DECLINED");
        assertThat(handler.apply(declined, WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.IGNORED);
    }

    @Test
    void declinedOldPaymentKeepsCampaignWaitingForTheNewOne() {
        payment.setStatus(AdPayment.FAILED);
        payment(campaign, "ADS-2");

        handler.apply(transaction("tx-2", "ADS-1", WompiTransaction.DECLINED, AMOUNT_CENTS), WompiEventSource.WEBHOOK);

        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PENDING_PAYMENT);
    }

    @Test
    void approvedForCancelledOrRejectedCampaignFlagsManualRefund() {
        campaign.setStatus(AdCampaignStatus.CANCELLED);

        assertThat(handler.apply(transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.APPLIED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.CANCELLED);
        assertThat(campaign.isRequiresManualRefund()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(AdPayment.APPROVED);
    }

    @Test
    void approvedAfterTheCampaignEndedCancelsItWithManualRefund() {
        campaign.setStartsOn(TODAY.minusDays(5));
        campaign.setEndsOn(TODAY.minusDays(1));

        handler.apply(transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS), WompiEventSource.WEBHOOK);

        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.CANCELLED);
        assertThat(campaign.isRequiresManualRefund()).isTrue();
    }

    @Test
    void secondApprovedPaymentForAPaidCampaignIsNotAppliedTwice() {
        handler.apply(transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS), WompiEventSource.WEBHOOK);
        AdPayment second = payment(campaign, "ADS-2");

        assertThat(handler.apply(transaction("tx-2", "ADS-2", WompiTransaction.APPROVED, AMOUNT_CENTS),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.APPLIED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.ACTIVE);
        assertThat(campaign.isRequiresManualRefund()).isTrue();
        assertThat(second.getStatus()).isEqualTo(AdPayment.APPROVED);
    }

    @Test
    void transactionLinkedToAnotherPaymentIsRejected() {
        AdPayment other = payment(campaign(AdCampaignStatus.ACTIVE, TODAY, TODAY), "ADS-9");
        other.setProviderTransactionId("tx-1");

        assertThat(handler.apply(transaction("tx-1", "ADS-1", WompiTransaction.APPROVED, AMOUNT_CENTS),
                WompiEventSource.WEBHOOK)).isEqualTo(WompiEventOutcome.REJECTED_MISMATCH);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PENDING_PAYMENT);
    }
}
