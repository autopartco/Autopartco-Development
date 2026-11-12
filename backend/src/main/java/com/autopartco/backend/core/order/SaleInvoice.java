package com.autopartco.backend.core.order;

import java.time.LocalDateTime;

/** Factura (PDF) que el almacén adjuntó a una venta. El archivo se descarga por su endpoint, nunca por URL directa. */
public record SaleInvoice(String fileName, long sizeBytes, LocalDateTime uploadedAt) {
}
