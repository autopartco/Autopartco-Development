package com.autopartco.backend.core.booking;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Vehículo de la cita ({@code service_bookings.vehicle}): marca y modelo del catálogo, año y placa opcionales (placa de
 * 5 a 7 letras o dígitos, sin guiones).
 */
public record BookingVehicle(
        @NotNull UUID makeId,
        @NotNull UUID modelId,
        @Min(1950) @Max(2100) Integer year,
        @Pattern(regexp = "^[A-Za-z0-9]{5,7}$", message = "La placa debe tener de 5 a 7 letras o números.")
        String plate
) {
    /** JSON que se guarda; la placa en mayúsculas. */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("makeId", makeId.toString());
        json.put("modelId", modelId.toString());
        if (year != null) {
            json.put("year", year);
        }
        if (plate != null && !plate.isBlank()) {
            json.put("plate", plate.strip().toUpperCase(Locale.ROOT));
        }
        return json;
    }

    /** Lee lo guardado; {@code null} si no hay vehículo o no se puede leer. */
    public static BookingVehicle fromJson(Map<String, Object> json) {
        if (json == null || json.get("makeId") == null || json.get("modelId") == null) {
            return null;
        }
        try {
            Object year = json.get("year");
            return new BookingVehicle(UUID.fromString(String.valueOf(json.get("makeId"))),
                    UUID.fromString(String.valueOf(json.get("modelId"))),
                    year instanceof Number number ? number.intValue() : null,
                    json.get("plate") == null ? null : String.valueOf(json.get("plate")));
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /** Sin placa (lo que ve el almacén en una cita cancelada). */
    public BookingVehicle withoutPlate() {
        return new BookingVehicle(makeId, modelId, year, null);
    }
}
