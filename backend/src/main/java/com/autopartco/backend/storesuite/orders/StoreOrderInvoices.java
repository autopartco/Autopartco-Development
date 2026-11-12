package com.autopartco.backend.storesuite.orders;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.OrderInvoiceService;
import com.autopartco.backend.core.order.SaleInvoice;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;

/** Factura de una venta del almacén: subir/reemplazar y quitar exigen {@code ORDER_UPDATE}; descargar, {@code ORDER_READ}. */
@Service
@AllArgsConstructor
public class StoreOrderInvoices {
    private final StoreAccess storeAccess;
    private final AccountLookup accountLookup;
    private final OrderInvoiceService invoices;
    private final AuditService auditService;

    public SaleInvoice upload(UUID storeId, UUID saleId, String email, MultipartFile file) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_UPDATE);
        UUID accountId = accountLookup.requireByEmail(email).accountId();
        SaleInvoice invoice = invoices.upload(storeId, saleId, accountId, file);
        auditService.record(accountId, "SALE_INVOICE_UPLOADED", "SALE", saleId,
                java.util.Map.of("storeId", storeId.toString(), "sizeBytes", invoice.sizeBytes()));
        return invoice;
    }

    public void delete(UUID storeId, UUID saleId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_UPDATE);
        UUID accountId = accountLookup.requireByEmail(email).accountId();
        invoices.delete(storeId, saleId);
        auditService.record(accountId, "SALE_INVOICE_DELETED", "SALE", saleId, java.util.Map.of("storeId", storeId.toString()));
    }

    public OrderInvoiceService.InvoiceFile download(UUID storeId, UUID saleId, String email) {
        storeAccess.requirePermission(email, storeId, StorePermissionCodes.ORDER_READ);
        return invoices.downloadForStore(storeId, saleId);
    }
}
