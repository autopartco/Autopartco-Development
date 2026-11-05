package com.autopartco.backend.storesuite.products;

import java.util.List;
import java.util.UUID;

/** Resultado de una acción masiva: productos afectados y omitidos con su motivo. */
public record BulkProductResult(int affected, List<Skipped> skipped) {
    public record Skipped(UUID productId, String code) {
    }
}
