package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import com.autopartco.backend.core.inventory.StockReturn;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiEventRouter;
import com.autopartco.backend.core.payments.WompiEventOutcome;
import com.autopartco.backend.core.payments.WompiEventSource;
import com.autopartco.backend.core.payments.WompiTransaction;

/**
 * Carrera entre una aprobación de Wompi y el vencimiento de la reserva. {@link FakeStore} imita el bloqueo de la fila
 * del pago: {@code lockByReference} espera ({@code FOR UPDATE}) y {@code lockExpired} omite la fila si está bloqueada
 * ({@code SKIP LOCKED}); el bloqueo se suelta al "confirmar" la transacción. Debe haber un solo ganador.
 */
class PaymentExpirationRaceTest {
    private static final String REFERENCE = MarketplaceWompiHandlerTest.REFERENCE;
    private static final UUID PAYMENT_ID = MarketplaceWompiHandlerTest.PAYMENT_ID;
    private static final UUID ORDER_ID = MarketplaceWompiHandlerTest.ORDER_ID;
    private static final UUID INVENTORY_ID = UUID.fromString("00000000-0000-0000-0000-00000000cc01");
    private static final int RESERVED_UNITS = 2;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void expirationThatLocksFirstWinsAndLateApprovalRequiresManualRefund() throws Exception {
        FakeStore store = new FakeStore();
        FakeStock stock = new FakeStock();
        CountDownLatch expirerLocked = new CountDownLatch(1);
        store.afterExpiredLock = () -> {
            expirerLocked.countDown();
            awaitQueuedThread(store.lock);
        };

        Future<Integer> expirer = executor.submit(() -> inTransaction(store, () -> expirer(store, stock)));
        assertThat(expirerLocked.await(5, TimeUnit.SECONDS)).isTrue();
        Future<WompiEventOutcome> approval = executor.submit(() -> inTransaction(store, () -> approve(store)));

        assertThat(expirer.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(approval.get(5, TimeUnit.SECONDS)).isEqualTo(WompiEventOutcome.APPLIED);
        assertThat(store.orderStatus).isEqualTo("CANCELLED");
        assertThat(store.status).isEqualTo("APPROVED");
        assertThat(store.manualRefund).isTrue();
        assertThat(stock.returned.get()).isEqualTo(RESERVED_UNITS);
    }

    @Test
    void approvalThatLocksFirstWinsAndExpirationSkipsThePayment() throws Exception {
        FakeStore store = new FakeStore();
        FakeStock stock = new FakeStock();
        CountDownLatch handlerLocked = new CountDownLatch(1);
        CountDownLatch expirerDone = new CountDownLatch(1);
        store.afterReferenceLock = () -> {
            handlerLocked.countDown();
            await(expirerDone);
        };

        Future<WompiEventOutcome> approval = executor.submit(() -> inTransaction(store, () -> approve(store)));
        assertThat(handlerLocked.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Integer> expirer = executor.submit(() -> {
            try {
                return inTransaction(store, () -> expirer(store, stock));
            } finally {
                expirerDone.countDown();
            }
        });

        assertThat(expirer.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(approval.get(5, TimeUnit.SECONDS)).isEqualTo(WompiEventOutcome.APPLIED);
        assertThat(store.orderStatus).isEqualTo("CONFIRMED");
        assertThat(store.status).isEqualTo("APPROVED");
        assertThat(store.manualRefund).isFalse();
        assertThat(stock.returned.get()).isZero();

        // La siguiente ronda ya no lo toma: el pago está aprobado.
        store.afterReferenceLock = () -> { };
        assertThat(inTransaction(store, () -> expirer(store, stock))).isZero();
        assertThat(stock.returned.get()).isZero();
    }

    @Test
    void concurrentApprovalAndExpirationAlwaysHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < 200; round++) {
            FakeStore store = new FakeStore();
            FakeStock stock = new FakeStock();
            CyclicBarrier start = new CyclicBarrier(2);
            Future<WompiEventOutcome> approval = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return inTransaction(store, () -> approve(store));
            });
            Future<Integer> expirer = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return inTransaction(store, () -> expirer(store, stock));
            });
            approval.get(5, TimeUnit.SECONDS);
            expirer.get(5, TimeUnit.SECONDS);
            // Una ronda posterior del vencimiento nunca cambia el resultado.
            inTransaction(store, () -> expirer(store, stock));

            assertThat(store.status).isEqualTo("APPROVED");
            boolean confirmed = store.orderStatus.equals("CONFIRMED")
                    && stock.returned.get() == 0 && !store.manualRefund;
            boolean expired = store.orderStatus.equals("CANCELLED")
                    && stock.returned.get() == RESERVED_UNITS && store.manualRefund;
            assertThat(confirmed ^ expired).as("round %d: %s", round, store).isTrue();
        }
    }

    private static WompiEventOutcome approve(FakeStore store) {
        MarketplaceWompiHandler handler = new MarketplaceWompiHandler(
                store, MarketplaceWompiHandlerTest.settings(WompiEnvironment.SANDBOX));
        return handler.apply(new WompiTransaction("t-1", REFERENCE, "APPROVED",
                MarketplaceWompiHandlerTest.CENTS, "COP", null), WompiEventSource.WEBHOOK);
    }

    /** Vencimiento sin transacciones en Wompi (devuelve cuántos pagos venció). */
    private static int expirer(FakeStore store, FakeStock stock) {
        WompiClient wompi = mock(WompiClient.class);
        when(wompi.findTransactionsByReference(REFERENCE)).thenReturn(List.of());
        return expirer(store, stock, wompi, mock(WompiEventRouter.class));
    }

    private static int expirer(FakeStore store, FakeStock stock, WompiClient wompi, WompiEventRouter router) {
        return new ExpireAwaitingPayments(store, stock, wompi, router,
                MarketplaceWompiHandlerTest.settings(WompiEnvironment.SANDBOX),
                TransactionOperations.withoutTransaction()).expireBatch(10).expired();
    }

    @Test
    void expiredPaymentApprovedInWompiIsConfirmedInsteadOfCancelled() throws Exception {
        FakeStore store = new FakeStore();
        FakeStock stock = new FakeStock();
        WompiTransaction approved = new WompiTransaction("t-1", REFERENCE, "APPROVED",
                MarketplaceWompiHandlerTest.CENTS, "COP", null);
        WompiClient wompi = mock(WompiClient.class);
        when(wompi.findTransactionsByReference(REFERENCE)).thenReturn(List.of(approved));
        // El router real abre su propia transacción: aquí el handler corre y suelta el bloqueo al terminar.
        WompiEventRouter router = mock(WompiEventRouter.class);
        when(router.apply(approved, WompiEventSource.RECONCILIATION))
                .thenAnswer(invocation -> inTransaction(store, () -> approve(store)));

        assertThat(inTransaction(store, () -> expirer(store, stock, wompi, router))).isZero();

        assertThat(store.orderStatus).isEqualTo("CONFIRMED");
        assertThat(store.status).isEqualTo("APPROVED");
        assertThat(store.manualRefund).isFalse();
        assertThat(stock.returned.get()).isZero();
    }

    /** Ejecuta el trabajo y suelta el bloqueo de fila al final, como el commit. */
    private static <T> T inTransaction(FakeStore store, java.util.concurrent.Callable<T> work) throws Exception {
        try {
            return work.call();
        } finally {
            while (store.lock.isHeldByCurrentThread()) {
                store.lock.unlock();
            }
        }
    }

    private static void awaitQueuedThread(ReentrantLock lock) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!lock.hasQueuedThreads()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("No thread waited for the payment lock");
            }
            Thread.onSpinWait();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /** Pago Wompi abierto de un pedido en espera, con la reserva ya vencida. */
    static final class FakeStore extends OrderPayments {
        final ReentrantLock lock = new ReentrantLock();
        volatile String status = "PENDING";
        volatile String orderStatus = "AWAITING_PAYMENT";
        volatile String providerStatus;
        volatile String providerTransactionId;
        volatile boolean manualRefund;
        final Instant expiresAt = Instant.now().minusSeconds(5);
        volatile Runnable afterReferenceLock = () -> { };
        volatile Runnable afterExpiredLock = () -> { };

        FakeStore() {
            super(null);
        }

        private OrderPayment snapshot() {
            return new OrderPayment(PAYMENT_ID, ORDER_ID, UUID.randomUUID(), 1L, orderStatus, "WOMPI", REFERENCE,
                    status, providerStatus, providerTransactionId, new BigDecimal("230000.00"), "COP", "SANDBOX",
                    expiresAt, null, manualRefund);
        }

        private boolean open() {
            return List.of("PENDING", "REJECTED", "CANCELLED").contains(status);
        }

        @Override
        public Optional<OrderPayment> lockByReference(String provider, String reference) {
            lock.lock();
            afterReferenceLock.run();
            return Optional.of(snapshot());
        }

        @Override
        public List<ExpiredCandidate> findExpired(String provider, ExpiredCandidate after, int limit) {
            return after == null && open() && !Instant.now().isBefore(expiresAt)
                    ? List.of(new ExpiredCandidate(PAYMENT_ID, ORDER_ID, REFERENCE, "SANDBOX", expiresAt))
                    : List.of();
        }

        @Override
        public Optional<ExpiredPayment> lockExpired(UUID paymentId) {
            if (!lock.tryLock()) {
                return Optional.empty();
            }
            if (!open() || Instant.now().isBefore(expiresAt)) {
                lock.unlock();
                return Optional.empty();
            }
            afterExpiredLock.run();
            return Optional.of(new ExpiredPayment(PAYMENT_ID, ORDER_ID));
        }

        @Override
        public boolean transactionLinkedToOtherPayment(String provider, String transactionId, UUID paymentId) {
            return false;
        }

        @Override
        public void markApproved(UUID paymentId, String transactionId, String providerStatus,
                boolean requiresManualRefund, String message) {
            requireLock();
            this.status = "APPROVED";
            this.providerTransactionId = transactionId;
            this.providerStatus = providerStatus;
            this.manualRefund = requiresManualRefund;
        }

        @Override
        public boolean confirmAwaitingOrder(UUID orderId) {
            requireLock();
            if (!orderStatus.equals("AWAITING_PAYMENT")) {
                return false;
            }
            orderStatus = "CONFIRMED";
            return true;
        }

        @Override
        public boolean cancelAwaitingOrder(UUID orderId, String reasonCode) {
            requireLock();
            if (!orderStatus.equals("AWAITING_PAYMENT")) {
                return false;
            }
            orderStatus = "CANCELLED";
            return true;
        }

        @Override
        public boolean markExpired(UUID paymentId, String message) {
            requireLock();
            if (!open()) {
                return false;
            }
            status = "EXPIRED";
            return true;
        }

        @Override
        public List<ReservedLine> reservedLines(UUID orderId) {
            return List.of(new ReservedLine(INVENTORY_ID, RESERVED_UNITS));
        }

        private void requireLock() {
            if (!lock.isHeldByCurrentThread()) {
                throw new IllegalStateException("Payment row must be locked");
            }
        }

        @Override
        public String toString() {
            return "FakeStore[status=" + status + ", orderStatus=" + orderStatus + ", manualRefund=" + manualRefund
                    + "]";
        }
    }

    static final class FakeStock extends StockService {
        final AtomicInteger returned = new AtomicInteger();

        FakeStock() {
            super(null, null);
        }

        @Override
        public void returnStock(List<StockReturn> returns) {
            returns.forEach(item -> returned.addAndGet(item.units()));
        }
    }
}
