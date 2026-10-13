package com.autopartco.backend.core.organization;

/** Datos de la solicitud de almacén que necesita el panel: nombre, estado del almacén y configuración pendiente. */
public record StoreApplicationSummary(
        String businessName,
        String storeStatus,
        boolean configurationPending
) {
}
