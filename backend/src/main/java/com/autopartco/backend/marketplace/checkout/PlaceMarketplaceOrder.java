package com.autopartco.backend.marketplace.checkout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.catalog.ListingKey;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AddressSnapshot;
import com.autopartco.backend.core.identity.PersonAddressLookup;
import com.autopartco.backend.core.inventory.StockDecrement;
import com.autopartco.backend.core.inventory.StockService;
import com.autopartco.backend.core.order.BuyerOrderQueries;
import com.autopartco.backend.core.order.NewOrder;
import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.order.PaymentReferenceOwner;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.PaymentRequiredException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso B1: el comprador confirma su carrito. Todo ocurre en una sola transacción; cualquier excepción
 * (stock insuficiente, cambio de precio, pago rechazado, clave repetida) revierte los descuentos de stock y no deja
 * pedido, venta ni pago. El precio sale siempre de {@link ProductResponse#effectivePrice()}, nunca del cliente.
 */
@Slf4j
@Service
@AllArgsConstructor
public class PlaceMarketplaceOrder {
    static final String CURRENCY = "COP";
    static final String DEFAULT_OUTCOME = "APPROVED";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Comparator<PlaceOrderRequest.Item> LOCK_ORDER = Comparator
            .comparing(PlaceOrderRequest.Item::branchId)
            .thenComparing(PlaceOrderRequest.Item::productId);

    private final AccountLookup accountLookup;
    private final PersonAddressLookup personAddressLookup;
    private final ListingQueries listingQueries;
    private final StockService stockService;
    private final OrderPlacement orderPlacement;
    private final BuyerOrderQueries buyerOrderQueries;
    private final CheckoutPaymentGateway gateway;

    @Transactional
    public CheckoutResult execute(String email, PlaceOrderRequest request) {
        // 1. Comprador autenticado.
        UUID personId = accountLookup.requireByEmail(email).personId();
        List<PlaceOrderRequest.Item> items = request.items();

        // 2. Sin pares producto-sucursal repetidos.
        rejectDuplicateLines(items);

        // 3. Método de pago disponible.
        String paymentMethod = request.paymentMethod();
        if (!gateway.enabled() || !orderPlacement.isActivePaymentMethod(paymentMethod)) {
            throw new BadRequestException(
                    "PAYMENT_METHOD_NOT_SUPPORTED",
                    "El método de pago no está disponible."
            );
        }

        // 4. Idempotencia por clientRequestId.
        String reference = request.clientRequestId().toString();
        Optional<PaymentReferenceOwner> existing = orderPlacement.findByPaymentReference(reference);
        if (existing.isPresent()) {
            PaymentReferenceOwner owner = existing.get();
            if (personId.equals(owner.personId())) {
                return new CheckoutResult(buyerOrderQueries.receipt(personId, owner.orderId()), true);
            }
            throw duplicateRequest();
        }

        // 5. Dirección propia y vigente (no se distingue ajena de inexistente).
        AddressSnapshot address = personAddressLookup.findOwned(personId, request.addressId())
                .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "No se encontró la dirección."));

        // 6. Listados comprables; se reportan todas las líneas faltantes.
        List<ListingKey> keys = items.stream().map(PlaceMarketplaceOrder::keyOf).toList();
        Map<ListingKey, ProductResponse> listings = listingQueries.purchasableListings(keys);
        List<Map<String, Object>> missing = new ArrayList<>();
        for (ListingKey key : keys) {
            if (!listings.containsKey(key)) {
                missing.add(lineRef(key));
            }
        }
        if (!missing.isEmpty()) {
            throw new NotFoundException(
                    "PRODUCT_NOT_FOUND",
                    "Uno o más repuestos ya no están disponibles. Quítalos del carrito para continuar.",
                    Map.of("items", missing)
            );
        }

        // 7. Descuento atómico en orden fijo (branchId, productId) para evitar interbloqueos.
        List<PlaceOrderRequest.Item> sorted = items.stream().sorted(LOCK_ORDER).toList();
        Map<ListingKey, StockDecrement> decrements = new HashMap<>();
        List<Map<String, Object>> shortages = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        for (PlaceOrderRequest.Item item : sorted) {
            ListingKey key = keyOf(item);
            Optional<StockDecrement> decrement =
                    stockService.decrementIfAvailable(item.branchId(), item.productId(), item.quantity());
            if (decrement.isEmpty()) {
                Map<String, Object> shortage = lineRef(key);
                shortage.put("requested", item.quantity());
                shortage.put("available", stockService.availableStock(item.branchId(), item.productId()));
                shortages.add(shortage);
                continue;
            }
            decrements.put(key, decrement.get());
            if (decrement.get().price().compareTo(listings.get(key).price()) != 0) {
                changed.add(lineRef(key));
            }
        }

        // 8. Lanzar revierte los UPDATE ya aplicados.
        if (!shortages.isEmpty()) {
            throw new ConflictException(
                    "INSUFFICIENT_STOCK",
                    "No hay unidades suficientes de uno o más repuestos. Ajusta las cantidades e inténtalo de nuevo.",
                    Map.of("items", shortages)
            );
        }
        if (!changed.isEmpty()) {
            throw new ConflictException(
                    "CHECKOUT_LISTING_CHANGED",
                    "El precio de uno o más repuestos cambió mientras comprabas. Revisa el carrito e inténtalo de nuevo.",
                    Map.of("items", changed)
            );
        }

        // 9. Una venta por sucursal con precios del servidor.
        Map<UUID, List<NewOrder.NewLine>> linesByBranch = new LinkedHashMap<>();
        Map<UUID, BigDecimal> subtotalByBranch = new LinkedHashMap<>();
        for (PlaceOrderRequest.Item item : sorted) {
            ListingKey key = keyOf(item);
            BigDecimal unitPrice = listings.get(key).effectivePrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            linesByBranch.computeIfAbsent(item.branchId(), branchId -> new ArrayList<>())
                    .add(new NewOrder.NewLine(decrements.get(key).inventoryId(), item.quantity(), unitPrice));
            subtotalByBranch.merge(item.branchId(), lineTotal, BigDecimal::add);
        }
        List<NewOrder.NewSale> sales = new ArrayList<>();
        BigDecimal orderTotal = ZERO;
        for (Map.Entry<UUID, List<NewOrder.NewLine>> entry : linesByBranch.entrySet()) {
            BigDecimal subtotal = subtotalByBranch.get(entry.getKey()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.add(ZERO);
            sales.add(new NewOrder.NewSale(entry.getKey(), subtotal, ZERO, total, List.copyOf(entry.getValue())));
            orderTotal = orderTotal.add(total);
        }

        // 10. Cobro. Un rechazo revierte todo y responde 402.
        String outcome = request.simulatedOutcome() == null ? DEFAULT_OUTCOME : request.simulatedOutcome();
        PaymentDecision decision = gateway.charge(
                new PaymentAttempt(paymentMethod, orderTotal, CURRENCY, reference, outcome));
        if (decision != PaymentDecision.APPROVED) {
            log.info("Pago de checkout rechazado: referencia={}, monto={}", reference, orderTotal);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("paymentMethod", paymentMethod);
            details.put("amount", orderTotal);
            details.put("currency", CURRENCY);
            throw new PaymentRequiredException(
                    "PAYMENT_REJECTED",
                    "El pago simulado fue rechazado. No se hizo ningún cobro ni se creó el pedido; "
                            + "puedes intentarlo con otro método.",
                    details
            );
        }

        // 11. Persistencia; una referencia repetida en carrera es un 409.
        NewOrder newOrder = new NewOrder(personId, toDeliveryAddress(address), paymentMethod, reference, sales);
        UUID orderId;
        try {
            orderId = orderPlacement.placeApproved(newOrder);
        } catch (DuplicateKeyException exception) {
            throw duplicateRequest();
        }
        log.info("Pedido de checkout creado: pedido={}, referencia={}, monto={}", orderId, reference, orderTotal);

        // 12. Recibo con la forma de §2.3.
        return new CheckoutResult(buyerOrderQueries.receipt(personId, orderId), false);
    }

    private static void rejectDuplicateLines(List<PlaceOrderRequest.Item> items) {
        Set<ListingKey> seen = new HashSet<>();
        Set<ListingKey> duplicated = new LinkedHashSet<>();
        for (PlaceOrderRequest.Item item : items) {
            ListingKey key = keyOf(item);
            if (!seen.add(key)) {
                duplicated.add(key);
            }
        }
        if (!duplicated.isEmpty()) {
            throw new BadRequestException(
                    "CHECKOUT_DUPLICATE_LINE",
                    "El pedido repite un mismo repuesto de la misma sucursal. Une las líneas e inténtalo de nuevo.",
                    Map.of("items", duplicated.stream().map(PlaceMarketplaceOrder::lineRef).toList())
            );
        }
    }

    private static ConflictException duplicateRequest() {
        return new ConflictException(
                "CHECKOUT_DUPLICATE_REQUEST",
                "Esta compra ya se está procesando o el identificador de la solicitud ya se usó. "
                        + "Recarga la página e inténtalo de nuevo."
        );
    }

    private static ListingKey keyOf(PlaceOrderRequest.Item item) {
        return new ListingKey(item.productId(), item.branchId());
    }

    private static Map<String, Object> lineRef(ListingKey key) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("productId", key.productId());
        ref.put("branchId", key.branchId());
        return ref;
    }

    private static NewOrder.DeliveryAddress toDeliveryAddress(AddressSnapshot address) {
        return new NewOrder.DeliveryAddress(
                address.addressLine(),
                address.addressComplement(),
                address.city(),
                address.department(),
                address.country(),
                address.postalCode(),
                address.latitude(),
                address.longitude()
        );
    }
}
