package com.autopartco.backend.storesuite.stockalerts;

import java.util.UUID;

/** Listado con existencias en o por debajo de su stock mínimo. */
public record LowStockResponse(
        UUID productId,
        String productName,
        String sku,
        UUID branchId,
        String branchName,
        int stock,
        int minStock
) {
}
