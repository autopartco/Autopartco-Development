package com.autopartco.backend.core.vehicle;

import java.util.UUID;

/** Compatibilidad marca/modelo/versión para formularios y filtros. */
public record CompatibilityOption(UUID id, String make, String model, String version) {
}
