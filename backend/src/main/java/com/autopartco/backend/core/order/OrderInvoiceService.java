package com.autopartco.backend.core.order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.storage.SupabaseStorageProperties;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

import lombok.AllArgsConstructor;

/**
 * Factura en PDF que el almacén adjunta a una venta (en Colombia factura el vendedor; Autopartco solo la guarda).
 * Se guarda en el bucket privado de negocio y solo se descarga a través del backend: el almacén dueño de la venta o
 * el comprador del pedido. Una vigente por venta; subir otra reemplaza la anterior.
 */
@Service
@AllArgsConstructor
public class OrderInvoiceService {
    private static final Logger log = LoggerFactory.getLogger(OrderInvoiceService.class);

    static final long MAX_SIZE = 5L * 1024 * 1024;
    static final String PDF = "application/pdf";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final JdbcTemplate jdbcTemplate;
    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;

    /** Contenido para descargar. */
    public record InvoiceFile(String fileName, byte[] content) {
    }

    @Transactional
    public SaleInvoice upload(UUID storeId, UUID saleId, UUID uploadedBy, MultipartFile file) {
        requireStoreSale(storeId, saleId);
        byte[] content = read(file);
        String fileName = cleanFileName(file.getOriginalFilename());
        String path = "sales/%s/invoice/%s.pdf".formatted(saleId, UUID.randomUUID());
        storageService.uploadToBucket(storageProperties.businessBucket(), path, content, PDF, false);
        try {
            List<String> previous = jdbcTemplate.queryForList(
                    "UPDATE order_invoices SET deleted_at = now() WHERE sale_id = ? AND deleted_at IS NULL"
                            + " RETURNING storage_path", String.class, saleId);
            jdbcTemplate.update("""
                    INSERT INTO order_invoices (sale_id, storage_path, file_name, content_type, size_bytes, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, saleId, path, fileName, PDF, content.length, uploadedBy);
            afterCommit(() -> previous.forEach(this::deleteQuietly));
        } catch (RuntimeException failure) {
            deleteQuietly(path);
            throw failure;
        }
        return current(saleId);
    }

    @Transactional
    public void delete(UUID storeId, UUID saleId) {
        requireStoreSale(storeId, saleId);
        List<String> removed = jdbcTemplate.queryForList(
                "UPDATE order_invoices SET deleted_at = now() WHERE sale_id = ? AND deleted_at IS NULL"
                        + " RETURNING storage_path", String.class, saleId);
        if (removed.isEmpty()) {
            throw notFound();
        }
        afterCommit(() -> removed.forEach(this::deleteQuietly));
    }

    /** Descarga para el almacén dueño de la venta. */
    @Transactional(readOnly = true)
    public InvoiceFile downloadForStore(UUID storeId, UUID saleId) {
        requireStoreSale(storeId, saleId);
        return download(saleId);
    }

    /** Descarga para el comprador: la venta debe pertenecer a un pedido suyo. 404 si no (no revela si existe). */
    @Transactional(readOnly = true)
    public InvoiceFile downloadForBuyer(UUID personId, UUID orderId, UUID saleId) {
        Boolean own = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM sales sale
                    JOIN orders customer_order ON customer_order.id = sale.order_id
                    WHERE sale.id = ? AND sale.order_id = ? AND customer_order.person_id = ?
                      AND sale.deleted_at IS NULL AND customer_order.deleted_at IS NULL)
                """, Boolean.class, saleId, orderId, personId);
        if (!Boolean.TRUE.equals(own)) {
            throw notFound();
        }
        return download(saleId);
    }

    private InvoiceFile download(UUID saleId) {
        List<String[]> rows = jdbcTemplate.query(
                "SELECT storage_path, file_name FROM order_invoices WHERE sale_id = ? AND deleted_at IS NULL",
                (result, row) -> new String[] {result.getString("storage_path"), result.getString("file_name")}, saleId);
        if (rows == null || rows.isEmpty()) {
            throw notFound();
        }
        String[] row = rows.getFirst();
        return new InvoiceFile(row[1], storageService.downloadFromBucket(storageProperties.businessBucket(), row[0]).content());
    }

    private SaleInvoice current(UUID saleId) {
        return jdbcTemplate.query(
                "SELECT file_name, size_bytes, created_at FROM order_invoices WHERE sale_id = ? AND deleted_at IS NULL",
                (result, row) -> new SaleInvoice(result.getString("file_name"), result.getLong("size_bytes"),
                        toLocalDateTime(result.getTimestamp("created_at"))), saleId).getFirst();
    }

    private void requireStoreSale(UUID storeId, UUID saleId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM sales sale
                    JOIN branches branch ON branch.id = sale.branch_id
                    WHERE sale.id = ? AND branch.store_id = ? AND sale.deleted_at IS NULL)
                """, Boolean.class, saleId, storeId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new NotFoundException("SELLER_ORDER_NOT_FOUND", "No se encontró la venta de este almacén.");
        }
    }

    static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "Selecciona el PDF de la factura.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BadRequestException("FILE_TOO_LARGE", "La factura no puede superar 5 MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("FILE_READ_FAILED", "No se pudo leer el archivo enviado.");
        }
        if (content.length > MAX_SIZE) {
            throw new BadRequestException("FILE_TOO_LARGE", "La factura no puede superar 5 MB.");
        }
        // Se comprueba el contenido, no la extensión ni el Content-Type que manda el cliente.
        if (content.length < PDF_MAGIC.length || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new BadRequestException("INVOICE_NOT_PDF", "La factura debe ser un archivo PDF.");
        }
        return content;
    }

    /** Nombre para mostrar y para la descarga: sin rutas ni caracteres de control, terminado en .pdf. */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        if (name.isEmpty() || name.equalsIgnoreCase(".pdf")) {
            name = "factura.pdf";
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name.length() > 200 ? name.substring(0, 196) + ".pdf" : name;
    }

    /** Borra archivos solo si la transacción se confirma (si se revierte, la factura vigente sigue intacta). */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void deleteQuietly(String path) {
        try {
            storageService.deleteFromBucket(storageProperties.businessBucket(), path);
        } catch (RuntimeException failure) {
            log.warn("Could not delete invoice file ({})", failure.getClass().getSimpleName());
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("INVOICE_NOT_FOUND", "Esta venta no tiene factura adjunta.");
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
