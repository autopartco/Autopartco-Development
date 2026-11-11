package com.autopartco.backend.shared.events;

import java.util.UUID;

/**
 * Evento de dominio: cambió algo visible en el perfil público o las sucursales públicas de un almacén (perfil, marca,
 * servicios, ajustes). Tras el commit vacía {@code store:profile} y {@code store:branches} de ese almacén. Los cambios
 * de sucursal, estado del almacén y calificación llegan por {@link ListingsChanged}.
 */
public record StoreProfileChanged(UUID storeId) {
}
