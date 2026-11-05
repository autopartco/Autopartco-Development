package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiTransaction;
import com.autopartco.backend.core.payments.WompiUnavailableException;

/** B7: los pagos vencidos que no pueden cerrarse en esta ronda no tapan a los posteriores. */
class MarketplacePaymentJobsTest {
    private static final Instant BASE = Instant.parse("2026-11-04T10:00:00Z");

    /** Pagos abiertos y vencidos en memoria, con la misma paginación por (expires_at, id) que el SQL. */
    static final class InMemoryPayments extends OrderPayments {
        final List<ExpiredCandidate> open = new ArrayList<>();
        final Set<UUID> expired = new HashSet<>();

        InMemoryPayments() {
            super(null);
        }

        void add(int index, String environment) {
            open.add(new ExpiredCandidate(new UUID(0, index), UUID.randomUUID(), "APM-" + index, environment,
                    BASE.plusSeconds(index)));
        }

        @Override
        public List<ExpiredCandidate> findExpired(String provider, ExpiredCandidate after, int limit) {
            Comparator<ExpiredCandidate> order = Comparator.comparing(ExpiredCandidate::expiresAt)
                    .thenComparing(ExpiredCandidate::paymentId);
            return open.stream()
                    .filter(candidate -> !expired.contains(candidate.paymentId()))
                    .filter(candidate -> after == null || order.compare(candidate, after) > 0)
                    .sorted(order)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ExpiredPayment> lockExpired(UUID paymentId) {
            return open.stream()
                    .filter(candidate -> candidate.paymentId().equals(paymentId) && !expired.contains(paymentId))
                    .findFirst()
                    .map(candidate -> new ExpiredPayment(paymentId, candidate.orderId()));
        }

        @Override
        public boolean cancelAwaitingOrder(UUID orderId, String reasonCode) {
            return true;
        }

        @Override
        public List<ReservedLine> reservedLines(UUID orderId) {
            return List.of();
        }

        @Override
        public boolean markExpired(UUID paymentId, String message) {
            return expired.add(paymentId);
        }
    }

    private static MarketplacePaymentJobs jobs(InMemoryPayments payments, WompiClient wompi, WompiEventRouter router) {
        ExpireAwaitingPayments expire = new ExpireAwaitingPayments(payments, mock(StockService.class), wompi, router,
                MarketplaceWompiHandlerTest.settings(WompiEnvironment.SANDBOX),
                TransactionOperations.withoutTransaction());
        return new MarketplacePaymentJobs(mock(ReconcilePendingPayments.class), expire);
    }

    @Test
    void fiftyPaymentsThatCannotExpireDoNotBlockLaterOnes() {
        InMemoryPayments payments = new InMemoryPayments();
        WompiClient wompi = mock(WompiClient.class);
        WompiEventRouter router = mock(WompiEventRouter.class);
        for (int index = 0; index < 50; index++) {
            payments.add(index, "SANDBOX");
            String reference = "APM-" + index;
            WompiTransaction approved = new WompiTransaction("t-" + index, reference, "APPROVED", 100, "COP", null);
            when(wompi.findTransactionsByReference(reference)).thenReturn(List.of(approved));
            // La aprobación no se puede aplicar: el pago no debe vencer en esta ronda.
            when(router.apply(approved, com.autopartco.backend.core.payments.WompiEventSource.RECONCILIATION))
                    .thenThrow(new IllegalStateException("db"));
        }
        for (int index = 50; index < 60; index++) {
            payments.add(index, "SANDBOX");
            when(wompi.findTransactionsByReference("APM-" + index)).thenReturn(List.of());
        }

        int expired = jobs(payments, wompi, router).expireAll();

        assertThat(expired).isEqualTo(10);
        assertThat(payments.expired).containsExactlyInAnyOrderElementsOf(
                payments.open.subList(50, 60).stream().map(OrderPayments.ExpiredCandidate::paymentId).toList());
    }

    @Test
    void wompiDownSkipsLookupsButLaterPaymentsOfAnotherEnvironmentStillExpire() {
        InMemoryPayments payments = new InMemoryPayments();
        for (int index = 0; index < 50; index++) {
            payments.add(index, "SANDBOX");
        }
        for (int index = 50; index < 55; index++) {
            payments.add(index, "PRODUCTION");
        }
        WompiClient wompi = mock(WompiClient.class);
        when(wompi.findTransactionsByReference(anyString())).thenThrow(new WompiUnavailableException("down"));

        int expired = jobs(payments, wompi, mock(WompiEventRouter.class)).expireAll();

        assertThat(expired).isEqualTo(5);
        // Tras el primer fallo no se vuelve a llamar a Wompi en la ronda.
        verify(wompi, times(1)).findTransactionsByReference(any());
    }
}
