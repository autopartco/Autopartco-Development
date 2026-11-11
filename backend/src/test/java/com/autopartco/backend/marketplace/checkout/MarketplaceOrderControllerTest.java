package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.payments.PaymentProvider;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class MarketplaceOrderControllerTest {
    private static final String EMAIL = "comprador@example.com";
    private static final UUID ORDER_ID = UUID.fromString("3f9b2c1e-7d4a-4c5b-8e1f-2a3b4c5d6e7f");

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createdOrderReturns201WithLocation() {
        ResponseEntity<?> response = place(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/me/orders/" + ORDER_ID);
        assertThat(((BuyerOrderResponse) response.getBody()).orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void replayedOrderReturns200WithSameBody() {
        ResponseEntity<?> response = place(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((BuyerOrderResponse) response.getBody()).orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void wompiOrderReturns202WithLocationAndPaymentLink() {
        ResponseEntity<?> response = placeWompi(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/me/orders/" + ORDER_ID);
        WompiCheckoutResponse body = (WompiCheckoutResponse) response.getBody();
        assertThat(body.status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(body.payment().checkoutUrl()).isEqualTo("https://checkout.example/p");
    }

    @Test
    void replayedWompiOrderReturns200WithSameShape() {
        ResponseEntity<?> response = placeWompi(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WompiCheckoutResponse.class);
    }

    @Test
    void checkoutOptionsDelegatesToService() {
        CheckoutOptions options = mock(CheckoutOptions.class);
        CheckoutOptionsResponse expected = new CheckoutOptionsResponse("WOMPI", "SANDBOX",
                List.of(new CheckoutOptionsResponse.Method("WOMPI", CheckoutOptions.WOMPI_NAME)));
        when(options.current()).thenReturn(expected);

        MarketplaceOrderController controller = new MarketplaceOrderController(
                mock(PlaceMarketplaceOrder.class), mock(StartMarketplaceCheckout.class), options,
                settings(PaymentProvider.WOMPI));

        assertThat(controller.checkoutOptions()).isSameAs(expected);
    }

    @Test
    void requestReadsTheOptionalAnonymousIdAsUuid() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        String base = "{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"addressId\":\"" + UUID.randomUUID()
                + "\",\"paymentMethod\":\"WOMPI\",\"items\":[{\"productId\":\"" + UUID.randomUUID()
                + "\",\"branchId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]";
        UUID anonymousId = UUID.randomUUID();

        PlaceOrderRequest with = mapper.readValue(base + ",\"anonymousId\":\"" + anonymousId + "\"}",
                PlaceOrderRequest.class);
        PlaceOrderRequest without = mapper.readValue(base + "}", PlaceOrderRequest.class);

        assertThat(with.anonymousId()).isEqualTo(anonymousId);
        assertThat(with.items()).hasSize(1);
        assertThat(without.anonymousId()).isNull();
        assertThat(validator.validate(with)).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.readValue(
                base + ",\"anonymousId\":\"not-a-uuid\"}", PlaceOrderRequest.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }

    @Test
    void visitorCookieTakesPriorityOverBodyAnonymousId() {
        UUID cookie = UUID.randomUUID();
        UUID body = UUID.randomUUID();
        org.springframework.mock.web.MockHttpServletRequest withCookie =
                new org.springframework.mock.web.MockHttpServletRequest();
        withCookie.setCookies(new jakarta.servlet.http.Cookie("ap_aid", cookie.toString()));
        PlaceOrderRequest fromBody = new PlaceOrderRequest(UUID.randomUUID(), UUID.randomUUID(), "WOMPI", null,
                List.of(item(1)), body);

        assertThat(MarketplaceOrderController.withVisitorCookie(fromBody, withCookie).anonymousId()).isEqualTo(cookie);
        // Sin cookie, el cuerpo.
        assertThat(MarketplaceOrderController.withVisitorCookie(fromBody,
                new org.springframework.mock.web.MockHttpServletRequest()).anonymousId()).isEqualTo(body);
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(validator.validate(request("SIMULATED_CARD", null, List.of(item(1))))).isEmpty();
        assertThat(validator.validate(request("SIMULATED_CARD", "REJECTED", List.of(item(99))))).isEmpty();
    }

    @Test
    void invalidRequestReportsFieldPaths() {
        List<PlaceOrderRequest.Item> items = new ArrayList<>(List.of(item(0), item(100)));
        PlaceOrderRequest request = new PlaceOrderRequest(null, null, " ", "MAYBE", items);

        assertThat(paths(validator.validate(request))).containsExactlyInAnyOrder(
                "clientRequestId", "addressId", "paymentMethod", "simulatedOutcome",
                "items[0].quantity", "items[1].quantity");
    }

    @Test
    void itemsMustHaveBetweenOneAndFiftyLines() {
        PlaceOrderRequest.Item[] tooMany = new PlaceOrderRequest.Item[51];
        Arrays.fill(tooMany, item(1));

        assertThat(paths(validator.validate(request("SIMULATED_CARD", null, List.of())))).containsExactly("items");
        assertThat(paths(validator.validate(request("SIMULATED_CARD", null, null)))).containsExactly("items");
        assertThat(paths(validator.validate(request("SIMULATED_CARD", null, List.of(tooMany)))))
                .containsExactly("items");
    }

    private static ResponseEntity<?> place(boolean replayed) {
        PlaceMarketplaceOrder useCase = mock(PlaceMarketplaceOrder.class);
        StartMarketplaceCheckout wompi = mock(StartMarketplaceCheckout.class);
        PlaceOrderRequest request = request("SIMULATED_CARD", null, List.of(item(1)));
        BuyerOrderResponse order = new BuyerOrderResponse(ORDER_ID, 1042L, "CONFIRMED", null, "COP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, List.of());
        when(useCase.execute(EMAIL, request)).thenReturn(new CheckoutResult(order, replayed));
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        ResponseEntity<?> response = new MarketplaceOrderController(useCase, wompi, mock(CheckoutOptions.class),
                settings(PaymentProvider.SIMULATED)).place(request, authentication);
        verifyNoInteractions(wompi);
        return response;
    }

    private static ResponseEntity<?> placeWompi(boolean replayed) {
        PlaceMarketplaceOrder simulated = mock(PlaceMarketplaceOrder.class);
        StartMarketplaceCheckout wompi = mock(StartMarketplaceCheckout.class);
        PlaceOrderRequest request = request("WOMPI", null, List.of(item(1)));
        WompiCheckoutResponse body = new WompiCheckoutResponse(ORDER_ID, 1042L, "AWAITING_PAYMENT",
                new WompiCheckoutResponse.Payment("APM-1", 23_000_000L, "COP", "https://checkout.example/p",
                        LocalDateTime.of(2026, 11, 4, 11, 0)));
        when(wompi.execute(EMAIL, request)).thenReturn(new WompiCheckoutResult(body, replayed));
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        ResponseEntity<?> response = new MarketplaceOrderController(simulated, wompi, mock(CheckoutOptions.class),
                settings(PaymentProvider.WOMPI)).place(request, authentication);
        verifyNoInteractions(simulated);
        return response;
    }

    private static PaymentSettings settings(PaymentProvider provider) {
        return new PaymentSettings(provider.name(), 30,
                new WompiProperties(WompiEnvironment.SANDBOX, null, null, null, null, null, null));
    }

    private static PlaceOrderRequest request(String method, String outcome, List<PlaceOrderRequest.Item> items) {
        return new PlaceOrderRequest(UUID.randomUUID(), UUID.randomUUID(), method, outcome, items);
    }

    private static PlaceOrderRequest.Item item(int quantity) {
        return new PlaceOrderRequest.Item(UUID.randomUUID(), UUID.randomUUID(), quantity);
    }

    private static Set<String> paths(Set<ConstraintViolation<PlaceOrderRequest>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
