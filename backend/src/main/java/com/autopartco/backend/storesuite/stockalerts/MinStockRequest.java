package com.autopartco.backend.storesuite.stockalerts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

/** Stock mínimo del listado; {@code null} vuelve al umbral por defecto. */
public record MinStockRequest(@PositiveOrZero @Max(1_000_000) Integer minStock) {
}
