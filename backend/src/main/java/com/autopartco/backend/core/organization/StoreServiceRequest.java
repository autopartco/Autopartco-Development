package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta o reemplazo de un servicio del almacén. {@code priceFrom} en COP ({@code null} = «a cotizar»). {@code active}
 * solo se lee en el PUT (si falta conserva el valor). {@code version} es opcional en el PUT: si no coincide con la
 * actual, 409 {@code SERVICE_VERSION_CONFLICT}.
 */
public record StoreServiceRequest(
        @NotNull(message = "La categoría es obligatoria.")
        ServiceCategory category,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
        @Pattern(regexp = "^[^<>]*$", message = "El nombre no puede contener HTML.")
        String name,

        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres.")
        @Pattern(regexp = "^[^<>]*$", message = "La descripción no puede contener HTML.")
        String description,

        @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
        @Digits(integer = 12, fraction = 2, message = "El precio admite hasta 2 decimales.")
        BigDecimal priceFrom,

        @Min(value = 5, message = "La duración mínima es de 5 minutos.")
        @Max(value = 10080, message = "La duración máxima es de 10080 minutos.")
        Integer durationMinutes,

        @Size(max = 100, message = "Demasiadas sucursales.")
        List<@NotNull UUID> branchIds,

        Integer sortOrder,
        Boolean active,
        Long version
) {
}
