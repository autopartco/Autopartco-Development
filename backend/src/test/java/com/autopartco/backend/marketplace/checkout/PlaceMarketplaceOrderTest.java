package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.identity.AddressSnapshot;
import com.autopartco.backend.core.identity.PersonAddressLookup;
import com.autopartco.backend.core.inventory.StockDecrement;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.BuyerOrderQueries;
import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.order.NewOrder;
import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.order.PaymentReferenceOwner;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.PaymentRequiredException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceMarketplaceOrderTest {
    private static final String EMAIL = "comprador@example.com";
    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_PERSON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final UUID REQUEST_ID = UUID.fromString("0b6f7a52-2f0e-4a3c-9d2e-6a1f8f6a4c11");

    // Sucursal 1 < sucursal 2 y producto 1 < producto 2 según UUID.compareTo.
    private static final UUID BRANCH_1 = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID BRANCH_2 = UUID.fromString("00000000-0000-0000-0000-000000000b02");
    private static final UUID PRODUCT_1 = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID PRODUCT_2 = UUID.fromString("00000000-0000-0000-0000-000000000a02");
    private static final UUID PRODUCT_3 = UUID.fromString("00000000-0000-0000-0000-000000000a03");

    @Mock
    private AccountLookup accountLookup;
    @Mock
    private PersonAddressLookup personAddressLookup;
    @Mock
    private ListingQueries listingQueries;
    @Mock
    private StockService stockService;
    @Mock
    private OrderPlacement orderPlacement;
    @Mock
    private BuyerOrderQueries buyerOrderQueries;
    @Mock
    private CheckoutPaymentGateway gateway;

    private PlaceMarketplaceOrder useCase;
    private final Map<ListingKey, ProductResponse> listings = new HashMap<>();
    private final BuyerOrderResponse receipt = receipt();

    @BeforeEach
    void setUp() {
        useCase = new PlaceMarketplaceOrder(accountLookup, personAddressLookup, listingQueries, stockService,
                orderPlacement, buyerOrderQueries, gateway);

        when(accountLookup.requireByEmail(EMAIL)).thenReturn(
                new AccountRef(UUID.randomUUID(), PERSON_ID, EMAIL, "Ana Pérez", "ACTIVE", "BUYER", "Ana"));
        when(gateway.enabled()).thenReturn(true);
        when(gateway.charge(any())).thenReturn(PaymentDecision.APPROVED);
        when(orderPlacement.isActivePaymentMethod("SIMULATED_CARD")).thenReturn(true);
        when(orderPlacement.findByPaymentReference(REQUEST_ID.toString())).thenReturn(Optional.empty());
        when(orderPlacement.placeApproved(any())).thenReturn(ORDER_ID);
        when(personAddressLookup.findOwned(PERSON_ID, ADDRESS_ID)).thenReturn(Optional.of(new AddressSnapshot(
                "Calle 10 # 20-30", "Apto 401", "Bogotá", "Cundinamarca", "Colombia", "110111", null, null)));
        when(listingQueries.purchasableListings(any())).thenAnswer(invocation -> {
            Collection<ListingKey> keys = invocation.getArgument(0);
            Map<ListingKey, ProductResponse> found = new HashMap<>();
            keys.forEach(key -> {
                if (listings.containsKey(key)) {
                    found.put(key, listings.get(key));
                }
            });
            return found;
        });
        when(buyerOrderQueries.receipt(eq(PERSON_ID), any())).thenReturn(receipt);
    }

    // 1
    @Test
    void approvedOrderWithTwoBranchesUsesServerEffectivePriceAndTotals() {
        listing(PRODUCT_1, BRANCH_1, "100000.00", "90000.00");
        listing(PRODUCT_2, BRANCH_2, "50000.00", "50000.00");
        listing(PRODUCT_3, BRANCH_2, "12500.50", "10000.25");
        UUID inv1 = stock(PRODUCT_1, BRANCH_1, 2, "100000.00");
        UUID inv2 = stock(PRODUCT_2, BRANCH_2, 1, "50000.00");
        UUID inv3 = stock(PRODUCT_3, BRANCH_2, 3, "12500.50");

        CheckoutResult result = useCase.execute(EMAIL, request("APPROVED",
                item(PRODUCT_2, BRANCH_2, 1), item(PRODUCT_1, BRANCH_1, 2), item(PRODUCT_3, BRANCH_2, 3)));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order()).isSameAs(receipt);

        ArgumentCaptor<NewOrder> captor = ArgumentCaptor.forClass(NewOrder.class);
        verify(orderPlacement).placeApproved(captor.capture());
        NewOrder order = captor.getValue();
        assertThat(order.personId()).isEqualTo(PERSON_ID);
        assertThat(order.paymentMethodCode()).isEqualTo("SIMULATED_CARD");
        assertThat(order.paymentReference()).isEqualTo(REQUEST_ID.toString());
        assertThat(order.address().addressLine()).isEqualTo("Calle 10 # 20-30");
        assertThat(order.sales()).hasSize(2);

        NewOrder.NewSale sale1 = order.sales().get(0);
        assertThat(sale1.branchId()).isEqualTo(BRANCH_1);
        assertThat(sale1.lines()).containsExactly(new NewOrder.NewLine(inv1, 2, new BigDecimal("90000.00")));
        assertThat(sale1.subtotal()).isEqualByComparingTo("180000.00");
        assertThat(sale1.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(sale1.total()).isEqualByComparingTo("180000.00");

        NewOrder.NewSale sale2 = order.sales().get(1);
        assertThat(sale2.branchId()).isEqualTo(BRANCH_2);
        assertThat(sale2.lines()).containsExactly(
                new NewOrder.NewLine(inv2, 1, new BigDecimal("50000.00")),
                new NewOrder.NewLine(inv3, 3, new BigDecimal("10000.25")));
        assertThat(sale2.subtotal()).isEqualByComparingTo("80000.75");
        assertThat(sale2.total()).isEqualByComparingTo("80000.75");
        assertThat(sale2.total().scale()).isEqualTo(2);

        ArgumentCaptor<PaymentAttempt> attempt = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(gateway).charge(attempt.capture());
        assertThat(attempt.getValue().amount()).isEqualByComparingTo("260000.75");
        assertThat(attempt.getValue().currency()).isEqualTo("COP");
        assertThat(attempt.getValue().reference()).isEqualTo(REQUEST_ID.toString());
        verify(buyerOrderQueries).receipt(PERSON_ID, ORDER_ID);
    }

    // 2
    @Test
    void rejectedPaymentThrows402AndNeverPlacesOrder() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");
        stock(PRODUCT_1, BRANCH_1, 2, "90000.00");
        when(gateway.charge(any())).thenReturn(PaymentDecision.REJECTED);

        assertThatThrownBy(() -> useCase.execute(EMAIL, request("REJECTED", item(PRODUCT_1, BRANCH_1, 2))))
                .isInstanceOfSatisfying(PaymentRequiredException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PAYMENT_REJECTED");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) exception.getDetails();
                    assertThat(details).containsEntry("paymentMethod", "SIMULATED_CARD")
                            .containsEntry("currency", "COP");
                    assertThat((BigDecimal) details.get("amount")).isEqualByComparingTo("180000.00");
                });

        ArgumentCaptor<PaymentAttempt> attempt = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(gateway).charge(attempt.capture());
        assertThat(attempt.getValue().simulatedOutcome()).isEqualTo("REJECTED");
        verify(orderPlacement, never()).placeApproved(any());
    }

    // 3
    @Test
    void twoLinesWithoutStockReportBothAndNeverCharge() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");
        listing(PRODUCT_2, BRANCH_2, "50000.00", "50000.00");
        when(stockService.decrementIfAvailable(BRANCH_1, PRODUCT_1, 3)).thenReturn(Optional.empty());
        when(stockService.decrementIfAvailable(BRANCH_2, PRODUCT_2, 5)).thenReturn(Optional.empty());
        when(stockService.availableStock(BRANCH_1, PRODUCT_1)).thenReturn(1);
        when(stockService.availableStock(BRANCH_2, PRODUCT_2)).thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(EMAIL,
                request(null, item(PRODUCT_1, BRANCH_1, 3), item(PRODUCT_2, BRANCH_2, 5))))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    List<Map<String, Object>> items = detailItems(exception.getDetails());
                    assertThat(items).hasSize(2);
                    assertThat(items.get(0)).containsEntry("productId", PRODUCT_1).containsEntry("branchId", BRANCH_1)
                            .containsEntry("requested", 3).containsEntry("available", 1);
                    assertThat(items.get(1)).containsEntry("productId", PRODUCT_2).containsEntry("branchId", BRANCH_2)
                            .containsEntry("requested", 5).containsEntry("available", 0);
                });

        verify(gateway, never()).charge(any());
        verify(orderPlacement, never()).placeApproved(any());
    }

    // 4
    @Test
    void missingListingReturns404WithAllMissingLinesAndNoStockChange() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");

        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null,
                item(PRODUCT_1, BRANCH_1, 1), item(PRODUCT_2, BRANCH_2, 1), item(PRODUCT_3, BRANCH_2, 1))))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
                    List<Map<String, Object>> items = detailItems(exception.getDetails());
                    assertThat(items).containsExactly(
                            Map.of("productId", PRODUCT_2, "branchId", BRANCH_2),
                            Map.of("productId", PRODUCT_3, "branchId", BRANCH_2));
                });

        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
        verify(gateway, never()).charge(any());
    }

    // 5
    @Test
    void foreignAddressReturns404() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");
        when(personAddressLookup.findOwned(PERSON_ID, ADDRESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null, item(PRODUCT_1, BRANCH_1, 1))))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ADDRESS_NOT_FOUND");
                    assertThat(exception.getMessage()).isEqualTo("No se encontró la dirección.");
                    assertThat(exception.getDetails()).isNull();
                });

        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
        verify(orderPlacement, never()).placeApproved(any());
    }

    // 6
    @Test
    void duplicatedLineReturns400() {
        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null,
                item(PRODUCT_1, BRANCH_1, 1), item(PRODUCT_2, BRANCH_1, 1), item(PRODUCT_1, BRANCH_1, 2))))
                .isInstanceOfSatisfying(BadRequestException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("CHECKOUT_DUPLICATE_LINE");
                    assertThat(detailItems(exception.getDetails()))
                            .containsExactly(Map.of("productId", PRODUCT_1, "branchId", BRANCH_1));
                });

        verify(listingQueries, never()).purchasableListings(any());
        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
    }

    // 7
    @Test
    void existingReferenceOfSamePersonReplaysWithoutTouchingStock() {
        when(orderPlacement.findByPaymentReference(REQUEST_ID.toString()))
                .thenReturn(Optional.of(new PaymentReferenceOwner(ORDER_ID, PERSON_ID)));

        CheckoutResult result = useCase.execute(EMAIL, request(null, item(PRODUCT_1, BRANCH_1, 1)));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order()).isSameAs(receipt);
        verify(buyerOrderQueries).receipt(PERSON_ID, ORDER_ID);
        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
        verify(gateway, never()).charge(any());
        verify(orderPlacement, never()).placeApproved(any());
    }

    // 8
    @Test
    void existingReferenceOfOtherPersonReturns409() {
        when(orderPlacement.findByPaymentReference(REQUEST_ID.toString()))
                .thenReturn(Optional.of(new PaymentReferenceOwner(ORDER_ID, OTHER_PERSON_ID)));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null, item(PRODUCT_1, BRANCH_1, 1))))
                .isInstanceOfSatisfying(ConflictException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CHECKOUT_DUPLICATE_REQUEST"));

        verify(buyerOrderQueries, never()).receipt(any(), any());
        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
    }

    // 9
    @Test
    void basePriceChangedDuringUpdateReturns409() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");
        listing(PRODUCT_2, BRANCH_2, "50000.00", "50000.00");
        stock(PRODUCT_1, BRANCH_1, 1, "95000.00");
        stock(PRODUCT_2, BRANCH_2, 1, "50000.0");

        assertThatThrownBy(() -> useCase.execute(EMAIL,
                request(null, item(PRODUCT_1, BRANCH_1, 1), item(PRODUCT_2, BRANCH_2, 1))))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("CHECKOUT_LISTING_CHANGED");
                    assertThat(detailItems(exception.getDetails()))
                            .containsExactly(Map.of("productId", PRODUCT_1, "branchId", BRANCH_1));
                });

        verify(gateway, never()).charge(any());
        verify(orderPlacement, never()).placeApproved(any());
    }

    // 10
    @Test
    void decrementsRunInBranchThenProductOrder() {
        listing(PRODUCT_1, BRANCH_1, "10.00", "10.00");
        listing(PRODUCT_2, BRANCH_1, "10.00", "10.00");
        listing(PRODUCT_1, BRANCH_2, "10.00", "10.00");
        listing(PRODUCT_3, BRANCH_2, "10.00", "10.00");
        stock(PRODUCT_1, BRANCH_1, 1, "10.00");
        stock(PRODUCT_2, BRANCH_1, 1, "10.00");
        stock(PRODUCT_1, BRANCH_2, 1, "10.00");
        stock(PRODUCT_3, BRANCH_2, 1, "10.00");

        useCase.execute(EMAIL, request(null,
                item(PRODUCT_3, BRANCH_2, 1), item(PRODUCT_2, BRANCH_1, 1),
                item(PRODUCT_1, BRANCH_2, 1), item(PRODUCT_1, BRANCH_1, 1)));

        InOrder order = inOrder(stockService);
        order.verify(stockService).decrementIfAvailable(BRANCH_1, PRODUCT_1, 1);
        order.verify(stockService).decrementIfAvailable(BRANCH_1, PRODUCT_2, 1);
        order.verify(stockService).decrementIfAvailable(BRANCH_2, PRODUCT_1, 1);
        order.verify(stockService).decrementIfAvailable(BRANCH_2, PRODUCT_3, 1);
    }

    // 11
    @Test
    void disabledSimulatorReturns400() {
        when(gateway.enabled()).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null, item(PRODUCT_1, BRANCH_1, 1))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PAYMENT_METHOD_NOT_SUPPORTED"));

        verify(orderPlacement, never()).findByPaymentReference(anyString());
        verify(stockService, never()).decrementIfAvailable(any(), any(), anyInt());
        verify(gateway, never()).charge(any());
    }

    @Test
    void inactivePaymentMethodReturns400() {
        PlaceOrderRequest request = new PlaceOrderRequest(REQUEST_ID, ADDRESS_ID, "WOMPI_CARD", null,
                List.of(item(PRODUCT_1, BRANCH_1, 1)));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request))
                .isInstanceOfSatisfying(BadRequestException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PAYMENT_METHOD_NOT_SUPPORTED"));
    }

    @Test
    void concurrentDuplicateKeyBecomes409() {
        listing(PRODUCT_1, BRANCH_1, "90000.00", "90000.00");
        stock(PRODUCT_1, BRANCH_1, 1, "90000.00");
        when(orderPlacement.placeApproved(any())).thenThrow(new DuplicateKeyException("payments_provider_reference"));

        assertThatThrownBy(() -> useCase.execute(EMAIL, request(null, item(PRODUCT_1, BRANCH_1, 1))))
                .isInstanceOfSatisfying(ConflictException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CHECKOUT_DUPLICATE_REQUEST"));
    }

    // --- utilidades ---

    private void listing(UUID productId, UUID branchId, String price, String effectivePrice) {
        listings.put(new ListingKey(productId, branchId), new ProductResponse(
                productId, UUID.randomUUID(), branchId, "Sede", "Repuesto", "BP-1", null,
                new BigDecimal(price), new BigDecimal(effectivePrice), null, null, 10,
                null, null, "ACTIVE", Map.of(), null, List.of()));
    }

    private UUID stock(UUID productId, UUID branchId, int units, String price) {
        UUID inventoryId = UUID.randomUUID();
        when(stockService.decrementIfAvailable(branchId, productId, units))
                .thenReturn(Optional.of(new StockDecrement(inventoryId, 5, new BigDecimal(price))));
        return inventoryId;
    }

    private static PlaceOrderRequest.Item item(UUID productId, UUID branchId, int quantity) {
        return new PlaceOrderRequest.Item(productId, branchId, quantity);
    }

    private static PlaceOrderRequest request(String outcome, PlaceOrderRequest.Item... items) {
        return new PlaceOrderRequest(REQUEST_ID, ADDRESS_ID, "SIMULATED_CARD", outcome, List.of(items));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> detailItems(Object details) {
        return (List<Map<String, Object>>) ((Map<String, Object>) details).get("items");
    }

    private static BuyerOrderResponse receipt() {
        return new BuyerOrderResponse(ORDER_ID, 1042L, "CONFIRMED", null, "COP", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, List.of());
    }
}
