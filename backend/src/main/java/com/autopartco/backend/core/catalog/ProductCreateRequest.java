package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Alta atómica de un producto (parte {@code data} del multipart, o cuerpo JSON sin imágenes). Exactamente uno de
 * {@code manufacturerId} / {@code newManufacturerName}. {@code vehicleRules} vacío o ausente se admite en borrador;
 * publicar exige al menos una o {@code universal=true} (que prohíbe reglas). {@code mainImageIndex} indexa la parte {@code images} (por defecto, la primera).
 */
public record ProductCreateRequest(
        @NotNull UUID branchId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String sku,
        @Size(max = 50) String partNumber,
        @NotBlank @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @PositiveOrZero Integer quantity,
        @NotNull UUID categoryId,
        UUID manufacturerId,
        @Size(min = 2, max = 100, message = "El nombre del fabricante debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del fabricante contiene caracteres no permitidos.")
        String newManufacturerName,
        Boolean confirmNewManufacturer,
        @Size(max = ProductVehicleRules.MAX_RULES) List<@Valid @NotNull VehicleRuleRequest> vehicleRules,
        Boolean universal,
        @Size(max = 50) Map<String, Object> attributes,
        @Min(0) @Max(9) Integer mainImageIndex,
        Boolean publish
) {
    public ProductCreateRequest {
        newManufacturerName = StoreManufacturerRequest.normalizeName(newManufacturerName);
        if (newManufacturerName != null && newManufacturerName.isEmpty()) {
            newManufacturerName = null;
        }
    }

    @AssertTrue(message = "Indica un fabricante existente o el nombre de uno nuevo, no ambos.")
    public boolean isManufacturerChoiceValid() {
        return (manufacturerId == null) != (newManufacturerName == null);
    }

    public boolean universalRequested() {
        return Boolean.TRUE.equals(universal);
    }

    public boolean publishRequested() {
        return Boolean.TRUE.equals(publish);
    }
}
