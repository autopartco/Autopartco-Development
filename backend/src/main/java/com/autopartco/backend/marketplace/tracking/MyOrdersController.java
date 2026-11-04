package com.autopartco.backend.marketplace.tracking;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.BuyerOrderQueries;
import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.order.BuyerOrderSummary;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** "Mis pedidos" del comprador autenticado: listado paginado y detalle con seguimiento del envío. */
@RestController
@AllArgsConstructor
public class MyOrdersController {
    private static final String ORDERS = ApiPaths.ME + "/orders";

    private final BuyerOrderQueries buyerOrderQueries;
    private final AccountLookup accountLookup;

    @GetMapping(ORDERS)
    public PageResponse<BuyerOrderSummary> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return buyerOrderQueries.list(personId(authentication), page, size);
    }

    @GetMapping(ORDERS + "/{orderId}")
    public BuyerOrderResponse detail(@PathVariable UUID orderId, Authentication authentication) {
        return buyerOrderQueries.detail(personId(authentication), orderId);
    }

    private UUID personId(Authentication authentication) {
        return accountLookup.requireByEmail(authentication.getName()).personId();
    }
}
