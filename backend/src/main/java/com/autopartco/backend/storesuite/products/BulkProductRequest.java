package com.autopartco.backend.storesuite.products;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Acción masiva sobre productos del almacén. {@code action}: {@code PUBLISH}, {@code UNPUBLISH}, {@code DELETE} o
 * {@code PRICE}. {@code PRICE} exige {@code branchId}, {@code priceMode} y {@code value}: {@code PERCENT} es la
 * variación porcentual (-90 a +500) y {@code AMOUNT} el nuevo precio absoluto (&gt; 0).
 */
public record BulkProductRequest(
        @NotEmpty @Size(max = BulkProductAction.MAX_PRODUCTS, message = "Selecciona entre 1 y 500 productos.")
        List<@NotNull UUID> productIds,
        @NotNull @Pattern(regexp = "PUBLISH|UNPUBLISH|DELETE|PRICE", message = "Acción no válida.")
        String action,
        UUID branchId,
        @Pattern(regexp = "PERCENT|AMOUNT", message = "Modo de precio no válido.")
        String priceMode,
        @Digits(integer = 10, fraction = 2) BigDecimal value
) {
    @AssertTrue(message = "El ajuste de precio exige branchId, priceMode y value.")
    public boolean isPriceFieldsPresent() {
        return !"PRICE".equals(action) || (branchId != null && priceMode != null && value != null);
    }
}
