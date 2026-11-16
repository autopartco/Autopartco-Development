package com.autopartco.backend.operations.payments;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.security.PlatformAccess;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Pedidos y pagos de toda la plataforma (solo lectura; permiso {@code PLATFORM_ORDERS_READ}). */
@RestController
@RequestMapping(ApiPaths.ADMIN)
@AllArgsConstructor
public class OrdersAdminController {
    private final OrdersAdmin ordersAdmin;

    @GetMapping("/orders")
    @PreAuthorize(PlatformAccess.ORDERS)
    public PageResponse<OrdersAdmin.OrderRow> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size
    ) {
        return ordersAdmin.orders(status, q, page, size);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize(PlatformAccess.ORDERS)
    public OrdersAdmin.OrderDetail order(@PathVariable UUID orderId) {
        return ordersAdmin.order(orderId);
    }

    @GetMapping("/payments")
    @PreAuthorize(PlatformAccess.ORDERS)
    public PageResponse<OrdersAdmin.PaymentRow> payments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size
    ) {
        return ordersAdmin.payments(status, provider, q, page, size);
    }
}
