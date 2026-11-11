package com.autopartco.backend.core.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.advertising.internal.AdCampaign;
import com.autopartco.backend.core.advertising.internal.AdCampaignRepository;
import com.autopartco.backend.core.advertising.internal.AdPayment;
import com.autopartco.backend.core.advertising.internal.AdPaymentRepository;
import com.autopartco.backend.core.advertising.internal.AdWompiHandler;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

class AdPaymentReconciliationTest {
    /** Reloj del job en UTC (las fechas de los pagos se comparan en la misma zona). */
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 11, 11, 15, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final long AMOUNT_CENTS = 15_000_000L;

    private final AdPaymentRepository paymentRepository = mock(AdPaymentRepository.class);
    private final AdCampaignRepository campaignRepository = mock(AdCampaignRepository.class);
    private final WompiClient wompiClient = mock(WompiClient.class);
    private final WompiEventRouter eventRouter = mock(WompiEventRouter.class);
    private final WompiProperties properties = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_x",
            "prv_test_x", "integrity", "events", "https://checkout.wompi.co/p/", "http://localhost/r");
    private final AdWompiHandler handler = new AdWompiHandler(paymentRepository, campaignRepository, properties,
            mock(AdCandidateCache.class), Clock.fixed(NOW, ZoneOffset.UTC));
    private final AdPaymentReconciliation reconciliation = new AdPaymentReconciliation(paymentRepository, handler,
            wompiClient, eventRouter, properties, TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final List<AdPayment> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(paymentRepository.findByCampaignIdAndStatus(any(), anyString())).thenAnswer(invocation ->
                payments.stream()
                        .filter(found -> found.getCampaignId().equals(invocation.getArgument(0))
                                && found.getStatus().equals(invocation.getArgument(1)))
                        .toList());
        when(paymentRepository.findPendingCreatedBefore(anyString(), anyString(), any(), any())).thenAnswer(
                invocation -> payments.stream()
                        .filter(found -> AdPayment.PENDING.equals(found.getStatus())
                                && found.getCreatedAt().isBefore(invocation.getArgument(2)))
                        .toList());
    }

    private AdCampaign campaign() {
        AdCampaign created = AdCampaign.builder()
                .id(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .name("c")
                .status(AdCampaignStatus.PENDING_PAYMENT)
                .startsOn(TODAY)
                .endsOn(TODAY.plusDays(9))
                .totalPrice(new BigDecimal("150000.00"))
                .createdBy(UUID.randomUUID())
                .productIds(new LinkedHashSet<>(List.of(UUID.randomUUID())))
                .build();
        when(campaignRepository.lockById(created.getId())).thenReturn(Optional.of(created));
        return created;
    }

    private AdPayment payment(AdCampaign owner, String reference, LocalDateTime createdAt) {
        AdPayment created = AdPayment.builder()
                .id(UUID.randomUUID())
                .campaignId(owner.getId())
                .reference(reference)
                .amountInCents(AMOUNT_CENTS)
                .currency("COP")
                .status(AdPayment.PENDING)
                .provider(AdPayment.WOMPI)
                .environment("SANDBOX")
                .createdAt(createdAt)
                .build();
        payments.add(created);
        when(paymentRepository.lockByReference(reference)).thenReturn(Optional.of(created));
        return created;
    }

    private static WompiTransaction transaction(String id, String reference, String status) {
        return new WompiTransaction(id, reference, status, AMOUNT_CENTS, "COP", null);
    }

    @Test
    void queriesPendingWompiPaymentsOlderThanFiveMinutesOfTheCurrentEnvironment() {
        reconciliation.reconcile();

        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findPendingCreatedBefore(eq("WOMPI"), eq("SANDBOX"), before.capture(),
                page.capture());
        assertThat(before.getValue()).isEqualTo(NOW_LOCAL.minusMinutes(5));
        assertThat(page.getValue().getPageSize()).isEqualTo(AdPaymentReconciliation.BATCH_SIZE);
    }

    @Test
    void appliesOnlyFinalTransactionsOfTheReferenceThroughTheRouter() {
        AdCampaign campaign = campaign();
        payment(campaign, "ADS-1", NOW_LOCAL.minusMinutes(30));
        WompiTransaction approved = transaction("tx-1", "ADS-1", WompiTransaction.APPROVED);
        when(wompiClient.findTransactionsByReference("ADS-1")).thenReturn(List.of(
                transaction("tx-0", "ADS-1", WompiTransaction.PENDING),
                transaction("tx-9", "ADS-9", WompiTransaction.APPROVED),
                approved));

        AdPaymentReconciliation.Result result = reconciliation.reconcile();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.expired()).isZero();
        verify(eventRouter).apply(approved, WompiEventSource.RECONCILIATION);
        verify(eventRouter, never()).apply(eq(transaction("tx-0", "ADS-1", WompiTransaction.PENDING)), any());
    }

    @Test
    void pendingWithoutTransactionForMoreThanADayFailsAndCampaignReturnsToDraft() {
        AdCampaign campaign = campaign();
        AdPayment stale = payment(campaign, "ADS-1", NOW_LOCAL.minusHours(25));
        when(wompiClient.findTransactionsByReference("ADS-1")).thenReturn(List.of());

        AdPaymentReconciliation.Result result = reconciliation.reconcile();

        assertThat(result.expired()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(AdPayment.FAILED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.DRAFT);
        verify(eventRouter, never()).apply(any(), any());
    }

    @Test
    void stalePendingWithAStillPendingWompiTransactionAlsoFails() {
        AdCampaign campaign = campaign();
        AdPayment stale = payment(campaign, "ADS-1", NOW_LOCAL.minusHours(30));
        when(wompiClient.findTransactionsByReference("ADS-1"))
                .thenReturn(List.of(transaction("tx-1", "ADS-1", WompiTransaction.PENDING)));

        reconciliation.reconcile();

        assertThat(stale.getStatus()).isEqualTo(AdPayment.FAILED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.DRAFT);
    }

    @Test
    void recentPendingPaymentIsNotExpired() {
        AdCampaign campaign = campaign();
        AdPayment recent = payment(campaign, "ADS-1", NOW_LOCAL.minusHours(23));
        when(wompiClient.findTransactionsByReference("ADS-1")).thenReturn(List.of());

        assertThat(reconciliation.reconcile().expired()).isZero();
        assertThat(recent.getStatus()).isEqualTo(AdPayment.PENDING);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PENDING_PAYMENT);
    }

    @Test
    void stalePaymentWithANewerPendingPaymentKeepsTheCampaignWaiting() {
        AdCampaign campaign = campaign();
        AdPayment stale = payment(campaign, "ADS-1", NOW_LOCAL.minusHours(26));
        payment(campaign, "ADS-2", NOW_LOCAL.minusMinutes(1));
        when(wompiClient.findTransactionsByReference("ADS-1")).thenReturn(List.of());

        reconciliation.reconcile();

        assertThat(stale.getStatus()).isEqualTo(AdPayment.FAILED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.PENDING_PAYMENT);
        verify(wompiClient, never()).findTransactionsByReference("ADS-2");
    }

    @Test
    void wompiDownStopsQueryingButStillExpiresStalePayments() {
        AdCampaign first = campaign();
        AdCampaign second = campaign();
        AdPayment stale = payment(first, "ADS-1", NOW_LOCAL.minusHours(26));
        AdPayment recent = payment(second, "ADS-2", NOW_LOCAL.minusMinutes(20));
        when(wompiClient.findTransactionsByReference("ADS-1"))
                .thenThrow(new WompiUnavailableException("Wompi API answered HTTP 503."));

        AdPaymentReconciliation.Result result = reconciliation.reconcile();

        assertThat(result.applied()).isZero();
        assertThat(result.expired()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(AdPayment.FAILED);
        assertThat(first.getStatus()).isEqualTo(AdCampaignStatus.DRAFT);
        assertThat(recent.getStatus()).isEqualTo(AdPayment.PENDING);
        verify(wompiClient, never()).findTransactionsByReference("ADS-2");
    }

    @Test
    void paymentResolvedByTheRouterIsNotExpiredAfterwards() {
        AdCampaign campaign = campaign();
        AdPayment stale = payment(campaign, "ADS-1", NOW_LOCAL.minusHours(26));
        WompiTransaction approved = transaction("tx-1", "ADS-1", WompiTransaction.APPROVED);
        when(wompiClient.findTransactionsByReference("ADS-1")).thenReturn(List.of(approved));
        // El router real aplica con el handler en su propia transacción.
        when(eventRouter.apply(approved, WompiEventSource.RECONCILIATION))
                .thenAnswer(invocation -> handler.apply(approved, WompiEventSource.RECONCILIATION));

        AdPaymentReconciliation.Result result = reconciliation.reconcile();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.expired()).isZero();
        assertThat(stale.getStatus()).isEqualTo(AdPayment.APPROVED);
        assertThat(campaign.getStatus()).isEqualTo(AdCampaignStatus.ACTIVE);
    }
}
