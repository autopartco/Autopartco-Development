package com.autopartco.backend.marketplace.tracking;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.BuyerOrderQueries;
import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.order.BuyerOrderSummary;
import com.autopartco.backend.core.order.OrderInvoiceService;
import com.autopartco.backend.core.order.OrderReceiptRenderer;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * "Mis pedidos" del comprador autenticado: listado paginado, detalle con seguimiento del envío, comprobante imprimible
 * y descarga de la factura que adjuntó cada almacén. Solo pedidos de la propia persona (404 si no).
 */
@RestController
@AllArgsConstructor
public class MyOrdersController {
    private static final String ORDERS = ApiPaths.ME + "/orders";

    private final BuyerOrderQueries buyerOrderQueries;
    private final OrderInvoiceService orderInvoiceService;
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

    /** Comprobante de compra en HTML (no es factura). */
    @GetMapping(ORDERS + "/{orderId}/receipt")
    public ResponseEntity<String> receipt(@PathVariable UUID orderId, Authentication authentication) {
        BuyerOrderResponse order = buyerOrderQueries.detail(personId(authentication), orderId);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(OrderReceiptRenderer.render(order));
    }

    /** Factura (PDF) que el almacén adjuntó a una venta del pedido. */
    @GetMapping(ORDERS + "/{orderId}/sales/{saleId}/invoice")
    public ResponseEntity<byte[]> invoice(@PathVariable UUID orderId, @PathVariable UUID saleId,
            Authentication authentication) {
        OrderInvoiceService.InvoiceFile file = orderInvoiceService.downloadForBuyer(personId(authentication), orderId, saleId);
        return pdf(file);
    }

    static ResponseEntity<byte[]> pdf(OrderInvoiceService.InvoiceFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    private UUID personId(Authentication authentication) {
        return accountLookup.requireByEmail(authentication.getName()).personId();
    }
}
