package com.autopartco.backend.core.vehicle;

import java.util.Map;
import java.util.UUID;

/**
 * Ids del catálogo de vehículos resueltos (solo registros vigentes). Un id ausente no existe o está borrado.
 *
 * @param makes nombre por id de marca
 * @param models modelo (con su marca) por id
 * @param versions versión (con su modelo) por id
 */
public record VehicleRefs(Map<UUID, String> makes, Map<UUID, Ref> models, Map<UUID, Ref> versions) {

    /** Modelo o versión: {@code parentId} es la marca (modelo) o el modelo (versión). */
    public record Ref(UUID id, UUID parentId, String name) {
    }
}
