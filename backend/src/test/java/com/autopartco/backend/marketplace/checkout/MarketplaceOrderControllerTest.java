package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
        ResponseEntity<BuyerOrderResponse> response = place(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/me/orders/" + ORDER_ID);
        assertThat(response.getBody().orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void replayedOrderReturns200WithSameBody() {
        ResponseEntity<BuyerOrderResponse> response = place(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().orderId()).isEqualTo(ORDER_ID);
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

    private static ResponseEntity<BuyerOrderResponse> place(boolean replayed) {
        PlaceMarketplaceOrder useCase = mock(PlaceMarketplaceOrder.class);
        PlaceOrderRequest request = request("SIMULATED_CARD", null, List.of(item(1)));
        BuyerOrderResponse order = new BuyerOrderResponse(ORDER_ID, 1042L, "CONFIRMED", null, "COP",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, List.of());
        when(useCase.execute(EMAIL, request)).thenReturn(new CheckoutResult(order, replayed));
        Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null);

        return new MarketplaceOrderController(useCase).place(request, authentication);
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
