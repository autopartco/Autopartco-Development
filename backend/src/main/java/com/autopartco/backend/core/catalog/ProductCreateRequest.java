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
        UUID branchId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String sku,
        @Size(max = 50) String partNumber,
        @NotBlank @Size(max = 5000) String description,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @PositiveOrZero Integer quantity,
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
        Boolean publish,
        @Size(min = 1, max = MAX_LISTINGS, message = "Indica entre 1 y 50 sucursales.")
        List<@Valid @NotNull Listing> listings
) {
    /** Máximo de sucursales por alta. */
    public static final int MAX_LISTINGS = 50;

    /** Existencias y precio opcional del producto en una sucursal. */
    public record Listing(
            @NotNull UUID branchId,
            @NotNull @PositiveOrZero Integer quantity,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price
    ) {
    }

    /** Cuerpo histórico (una sola sucursal con {@code branchId} y {@code quantity}), sin {@code listings}. */
    public ProductCreateRequest(UUID branchId, String name, String sku, String partNumber, String description,
            BigDecimal price, Integer quantity, UUID categoryId, UUID manufacturerId, String newManufacturerName,
            Boolean confirmNewManufacturer, List<VehicleRuleRequest> vehicleRules, Boolean universal,
            Map<String, Object> attributes, Integer mainImageIndex, Boolean publish) {
        this(branchId, name, sku, partNumber, description, price, quantity, categoryId, manufacturerId,
                newManufacturerName, confirmNewManufacturer, vehicleRules, universal, attributes, mainImageIndex,
                publish, null);
    }

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

    @AssertTrue(message = "Indica una sucursal con existencias (branchId y quantity) o la lista listings, no ambas.")
    public boolean isBranchSelectionValid() {
        boolean hasListings = listings != null && !listings.isEmpty();
        return hasListings ? branchId == null && quantity == null : branchId != null && quantity != null;
    }

    @AssertTrue(message = "Las sucursales de listings no pueden repetirse.")
    public boolean isListingBranchesDistinct() {
        if (listings == null) {
            return true;
        }
        List<UUID> ids = listings.stream()
                .filter(listing -> listing != null && listing.branchId() != null)
                .map(Listing::branchId)
                .toList();
        return ids.stream().distinct().count() == ids.size();
    }

    @AssertTrue(message = "Indica el precio general o el precio de cada sucursal.")
    public boolean isPriceValid() {
        if (price != null) {
            return true;
        }
        return listings != null && !listings.isEmpty()
                && listings.stream().allMatch(listing -> listing != null && listing.price() != null);
    }

    /** Listados a crear: los de {@code listings} o el único de {@code branchId}/{@code quantity}, con su precio. */
    public List<Listing> effectiveListings() {
        if (listings != null && !listings.isEmpty()) {
            return listings.stream()
                    .map(listing -> new Listing(listing.branchId(), listing.quantity(),
                            listing.price() != null ? listing.price() : price))
                    .toList();
        }
        return List.of(new Listing(branchId, quantity, price));
    }

    public boolean universalRequested() {
        return Boolean.TRUE.equals(universal);
    }

    public boolean publishRequested() {
        return Boolean.TRUE.equals(publish);
    }
}
