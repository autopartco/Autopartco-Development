package com.autopartco.backend.core.vehicle;

import java.util.UUID;

/** Versión de un modelo; {@code label} es el texto de la columna {@code versions.version}. */
public record VersionOption(UUID id, String label) {
}
