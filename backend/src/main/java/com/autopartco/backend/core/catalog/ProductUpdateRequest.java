package com.autopartco.backend.core.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Edición atómica (parte {@code data} del multipart, o cuerpo JSON sin imágenes nuevas). Campos nulos = sin cambio.
 * {@code vehicleRules} reemplaza el conjunto ({@code []} lo vacía). {@code publish}: {@code true} publica (exige
 * requisitos), {@code false} retira. Galería: {@code removeImageIds}, {@code imageOrder} (ids existentes y
 * {@code new:<i>} de la parte {@code newImages}) y {@code mainImage} (id o {@code new:<i>}).
 */
public record ProductUpdateRequest(
        @Size(max = 200)
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío.")
        String name,
        @Size(max = 100) String sku,
        @Size(max = 50) String partNumber,
        @Pattern(regexp = "(?s).*\\S.*", message = "La descripción no puede estar vacía.")
        @Size(max = 5000) String description,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @PositiveOrZero Integer quantity,
        UUID categoryId,
        UUID manufacturerId,
        @Size(min = 2, max = 100, message = "El nombre del fabricante debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del fabricante contiene caracteres no permitidos.")
        String newManufacturerName,
        Boolean confirmNewManufacturer,
        @Size(max = ProductVehicleRules.MAX_RULES) List<@Valid @NotNull VehicleRuleRequest> vehicleRules,
        Boolean universal,
        @Size(max = 50) Map<String, Object> attributes,
        Boolean publish,
        @Size(max = 10) Set<@NotNull UUID> removeImageIds,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> imageOrder,
        @Size(max = 40) String mainImage
) {
    public ProductUpdateRequest {
        newManufacturerName = StoreManufacturerRequest.normalizeName(newManufacturerName);
        if (newManufacturerName != null && newManufacturerName.isEmpty()) {
            newManufacturerName = null;
        }
    }

    @AssertTrue(message = "Indica un fabricante existente o el nombre de uno nuevo, no ambos.")
    public boolean isManufacturerChoiceValid() {
        return manufacturerId == null || newManufacturerName == null;
    }

    /** {@code true} si cambia algún dato del producto o del listado (sin contar la galería ni la publicación). */
    public boolean changesProductData() {
        return name != null || partNumber != null || sku != null || description != null || price != null
                || quantity != null || manufacturerId != null || newManufacturerName != null || categoryId != null
                || vehicleRules != null || universal != null || attributes != null;
    }

    /** {@code true} si toca la galería (además de las imágenes nuevas, que viajan en otra parte). */
    public boolean changesGallery() {
        return (removeImageIds != null && !removeImageIds.isEmpty()) || imageOrder != null || mainImage != null;
    }
}
