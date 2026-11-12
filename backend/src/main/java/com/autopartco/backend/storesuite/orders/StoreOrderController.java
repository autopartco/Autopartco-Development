package com.autopartco.backend.storesuite.orders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.order.OrderInvoiceService;
import com.autopartco.backend.core.order.SaleDetailResponse;
import com.autopartco.backend.core.order.SaleInvoice;
import com.autopartco.backend.core.order.SaleStatusRequest;
import com.autopartco.backend.core.order.SaleSummaryResponse;
import com.autopartco.backend.core.order.ShipmentCreateRequest;
import com.autopartco.backend.core.order.ShipmentStatusRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Ventas y envíos del almacén. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/orders")
@AllArgsConstructor
public class StoreOrderController {
    private final ListStoreOrders listStoreOrders;
    private final GetStoreOrder getStoreOrder;
    private final UpdateStoreOrderStatus updateStoreOrderStatus;
    private final CreateStoreShipment createStoreShipment;
    private final UpdateStoreShipmentStatus updateStoreShipmentStatus;
    private final StoreOrderInvoices storeOrderInvoices;

    @GetMapping
    public List<SaleSummaryResponse> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return listStoreOrders.execute(storeId, authentication.getName(), status, branchId, page, size);
    }

    @GetMapping("/{saleId}")
    public SaleDetailResponse get(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            Authentication authentication
    ) {
        return getStoreOrder.execute(storeId, saleId, authentication.getName());
    }

    @PatchMapping("/{saleId}/status")
    public SaleDetailResponse updateStatus(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody SaleStatusRequest request,
            Authentication authentication
    ) {
        return updateStoreOrderStatus.execute(storeId, saleId, authentication.getName(), request);
    }

    @PostMapping("/{saleId}/shipment")
    public SaleDetailResponse createShipment(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody ShipmentCreateRequest request,
            Authentication authentication
    ) {
        return createStoreShipment.execute(storeId, saleId, authentication.getName(), request);
    }

    @PatchMapping("/{saleId}/shipment")
    public SaleDetailResponse updateShipmentStatus(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody ShipmentStatusRequest request,
            Authentication authentication
    ) {
        return updateStoreShipmentStatus.execute(storeId, saleId, authentication.getName(), request);
    }

    /** Adjunta (o reemplaza) la factura en PDF de la venta; campo multipart {@code file}, máximo 5 MB. */
    @PostMapping(value = "/{saleId}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SaleInvoice uploadInvoice(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return storeOrderInvoices.upload(storeId, saleId, authentication.getName(), file);
    }

    @DeleteMapping("/{saleId}/invoice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvoice(@PathVariable UUID storeId, @PathVariable UUID saleId, Authentication authentication) {
        storeOrderInvoices.delete(storeId, saleId, authentication.getName());
    }

    @GetMapping("/{saleId}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            Authentication authentication
    ) {
        OrderInvoiceService.InvoiceFile file = storeOrderInvoices.download(storeId, saleId, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
