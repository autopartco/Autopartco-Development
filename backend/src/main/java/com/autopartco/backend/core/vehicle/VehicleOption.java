package com.autopartco.backend.core.vehicle;

import java.util.UUID;

/** Marca o modelo del catálogo de vehículos (id + nombre). */
public record VehicleOption(UUID id, String name) {
}
