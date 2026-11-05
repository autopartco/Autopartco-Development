package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.order.NewOrder;
import com.autopartco.backend.core.order.OrderPayment;
import com.autopartco.backend.core.order.OrderPayments;
import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.order.PaymentReferenceOwner;
import com.autopartco.backend.core.order.PendingPayment;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiClient;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StartMarketplaceCheckoutTest {
    static final WompiProperties WOMPI = new WompiProperties(WompiEnvironment.SANDBOX, "pub_test_abc",
            "prv_test_secret", "integrity", "events", "https://checkout.wompi.co/p/", "https://front.example/portal");
    private static final String EMAIL = "comprador@example.com";
    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ORDER_ID = UUID.fromString("9b2f6a3e-1c4d-4e8f-a1b2-3c4d5e6f7a8b");
    private static final UUID REQUEST_ID = UUID.fromString("6f1c2c1e-7d3b-4c55-9a43-1b2f0c9e5a10");
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private AccountLookup accountLookup;
    @Mock
    private OrderPlacement orderPlacement;
    @Mock
    private OrderPayments orderPayments;
    @Mock
    private PlaceMarketplaceOrder placeMarketplaceOrder;

    private StartMarketplaceCheckout useCase;
    private final AtomicReference<PendingPayment> stored = new AtomicReference<>();

    static MarketplacePaymentLinks links() {
        return new MarketplacePaymentLinks(new WompiClient(WOMPI, new ObjectMapper()),
                new AppProperties("http://localhost:8081", "https://front.example/", null, null, null));
    }

    @BeforeEach
    void setUp() {
        useCase = new StartMarketplaceCheckout(accountLookup, orderPlacement, orderPayments, placeMarketplaceOrder,
                links(), new PaymentSettings("WOMPI", 30, WOMPI), Clock.fixed(NOW, ZoneOffset.UTC));
        when(accountLookup.requireByEmail(EMAIL)).thenReturn(
                new AccountRef(UUID.randomUUID(), PERSON_ID, EMAIL, "Ana Pérez", "ACTIVE", "BUYER", "Ana"));
        when(orderPlacement.isActivePaymentMethod("WOMPI")).thenReturn(true);
        when(orderPlacement.findByIdempotencyKey(REQUEST_ID.toString())).thenReturn(Optional.empty());
        when(placeMarketplaceOrder.prepare(any(), any())).thenReturn(new PreparedOrder(
                new NewOrder.DeliveryAddress("Calle 10", null, "Bogotá", "Cundinamarca", "Colombia", null, null, null),
                List.of(new NewOrder.NewSale(UUID.randomUUID(), new BigDecimal("230000.00"),
                        new BigDecimal("0.00"), new BigDecimal("230000.00"),
                        List.of(new NewOrder.NewLine(UUID.randomUUID(), UUID.randomUUID(), 2,
                                new BigDecimal("115000.00"))))),
                new BigDecimal("230000.00")));
        when(orderPlacement.placeAwaitingPayment(any(), any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return ORDER_ID;
        });
        when(orderPayments.findForBuyer(PERSON_ID, ORDER_ID)).thenAnswer(invocation ->
                Optional.of(snapshot("AWAITING_PAYMENT", "PENDING", stored.get())));
    }

    private static OrderPayment snapshot(String orderStatus, String status, PendingPayment payment) {
        return new OrderPayment(UUID.randomUUID(), ORDER_ID, PERSON_ID, 1042L, orderStatus, payment.provider(),
                payment.reference(), status, null, null, new BigDecimal("230000.00"), "COP",
                payment.environment(), payment.expiresAt(), null, false);
    }

    private static PlaceOrderRequest request(String method, String outcome) {
        return new PlaceOrderRequest(REQUEST_ID, UUID.randomUUID(), method, outcome,
                List.of(new PlaceOrderRequest.Item(UUID.randomUUID(), UUID.randomUUID(), 2)));
    }

    @Test
    void startsPaymentWithReservationAndSignedLink() {
        PlaceOrderRequest request = request("WOMPI", null);

        WompiCheckoutResult result = useCase.execute(EMAIL, request);

        assertThat(result.replayed()).isFalse();
        verify(placeMarketplaceOrder).prepare(PERSON_ID, request);
        ArgumentCaptor<NewOrder> order = ArgumentCaptor.forClass(NewOrder.class);
        verify(orderPlacement).placeAwaitingPayment(order.capture(), any());
        assertThat(order.getValue().paymentMethodCode()).isEqualTo("WOMPI");

        PendingPayment payment = stored.get();
        assertThat(payment.provider()).isEqualTo("WOMPI");
        assertThat(payment.reference()).matches("APM-[0-9a-f]{32}");
        assertThat(payment.idempotencyKey()).isEqualTo(REQUEST_ID.toString());
        assertThat(payment.environment()).isEqualTo("SANDBOX");
        assertThat(payment.expiresAt()).isEqualTo(NOW.plusSeconds(30 * 60));

        WompiCheckoutResponse response = result.response();
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.orderNumber()).isEqualTo(1042L);
        assertThat(response.status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.payment().reference()).isEqualTo(payment.reference());
        assertThat(response.payment().amountInCents()).isEqualTo(23_000_000L);
        assertThat(response.payment().currency()).isEqualTo("COP");
        assertThat(response.payment().expiresAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 10, 30));
        assertThat(response.payment().checkoutUrl())
                .startsWith("https://checkout.wompi.co/p/?public-key=pub_test_abc")
                .contains("amount-in-cents=23000000", "reference=" + payment.reference(),
                        "expiration-time=2026-11-04T15:30:00.000Z",
                        "redirect-url=https://front.example/pedido/" + ORDER_ID + "/resultado");
    }

    @Test
    void replayOfSameBuyerReturnsCurrentStateWithoutTouchingStock() {
        PendingPayment previous = new PendingPayment("WOMPI", "APM-0123456789abcdef0123456789abcdef",
                REQUEST_ID.toString(), "SANDBOX", NOW.minusSeconds(60));
        when(orderPlacement.findByIdempotencyKey(REQUEST_ID.toString()))
                .thenReturn(Optional.of(new PaymentReferenceOwner(ORDER_ID, PERSON_ID)));
        doReturn(Optional.of(snapshot("CANCELLED", "EXPIRED", previous)))
                .when(orderPayments).findForBuyer(PERSON_ID, ORDER_ID);

        WompiCheckoutResult result = useCase.execute(EMAIL, request("WOMPI", null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().status()).isEqualTo("CANCELLED");
        assertThat(result.response().payment().reference()).isEqualTo(previous.reference());
        assertThat(result.response().payment().checkoutUrl()).isNull();
        verify(placeMarketplaceOrder, never()).prepare(any(), any());
        verify(orderPlacement, never()).placeAwaitingPayment(any(), any());
    }

    @Test
    void replayWhilePayableKeepsTheLink() {
        PendingPayment previous = new PendingPayment("WOMPI", "APM-0123456789abcdef0123456789abcdef",
                REQUEST_ID.toString(), "SANDBOX", NOW.plusSeconds(600));
        when(orderPlacement.findByIdempotencyKey(REQUEST_ID.toString()))
                .thenReturn(Optional.of(new PaymentReferenceOwner(ORDER_ID, PERSON_ID)));
        doReturn(Optional.of(snapshot("AWAITING_PAYMENT", "REJECTED", previous)))
                .when(orderPayments).findForBuyer(PERSON_ID, ORDER_ID);

        WompiCheckoutResult result = useCase.execute(EMAIL, request("WOMPI", null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().payment().checkoutUrl()).contains("reference=" + previous.reference());
    }

    @Test
    void replayOfAnotherBuyerIsConflict() {
        when(orderPlacement.findByIdempotencyKey(REQUEST_ID.toString()))
                .thenReturn(Optional.of(new PaymentReferenceOwner(ORDER_ID, UUID.randomUUID())));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request("WOMPI", null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("CHECKOUT_DUPLICATE_REQUEST"));
        verify(placeMarketplaceOrder, never()).prepare(any(), any());
    }

    @Test
    void simulatedOutcomeIsNotAllowed() {
        assertThatThrownBy(() -> useCase.execute(EMAIL, request("WOMPI", "APPROVED")))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SIMULATED_OUTCOME_NOT_ALLOWED"));
        verify(placeMarketplaceOrder, never()).prepare(any(), any());
    }

    @Test
    void simulatedOrInactiveMethodIsNotSupported() {
        assertThatThrownBy(() -> useCase.execute(EMAIL, request("SIMULATED_CARD", null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PAYMENT_METHOD_NOT_SUPPORTED"));

        when(orderPlacement.isActivePaymentMethod("WOMPI")).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute(EMAIL, request("WOMPI", null)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PAYMENT_METHOD_NOT_SUPPORTED"));
        verify(placeMarketplaceOrder, never()).prepare(any(), any());
    }

    @Test
    void concurrentDuplicateKeyBecomes409() {
        when(orderPlacement.placeAwaitingPayment(any(), any()))
                .thenThrow(new DuplicateKeyException("uq_payments__idempotency_key"));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request("WOMPI", null)))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("CHECKOUT_DUPLICATE_REQUEST"));
    }

    @Test
    void amountWithMoreThanTwoDecimalsFailsInsteadOfRounding() {
        when(placeMarketplaceOrder.prepare(any(), any())).thenReturn(new PreparedOrder(null, List.of(),
                new BigDecimal("100.005")));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request("WOMPI", null)))
                .isInstanceOf(ArithmeticException.class);
        verify(orderPlacement, never()).placeAwaitingPayment(any(), any());
    }
}
