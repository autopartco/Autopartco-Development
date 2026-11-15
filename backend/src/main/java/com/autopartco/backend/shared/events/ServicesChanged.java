package com.autopartco.backend.shared.events;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evento de dominio: se creó, editó, activó o desactivó un servicio del almacén (o cambiaron las sucursales que lo
 * prestan). Lo publica {@code core.organization} dentro de su transacción; {@code core.search} lo escucha tras el commit
 * y reindexa {@code search_services} solo para esos servicios. Los cambios de sucursal o de estado del almacén llegan
 * por {@link ListingsChanged}.
 *
 * @param serviceIds servicios afectados ({@code store_services.id})
 */
public record ServicesChanged(Set<UUID> serviceIds) {
    public ServicesChanged {
        serviceIds = serviceIds == null ? Set.of()
                : Set.copyOf(serviceIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    public static ServicesChanged of(UUID serviceId) {
        return new ServicesChanged(serviceId == null ? Set.of() : Set.of(serviceId));
    }

    public static ServicesChanged of(Collection<UUID> serviceIds) {
        return new ServicesChanged(serviceIds == null ? Set.of() : Set.copyOf(serviceIds));
    }

    public boolean isEmpty() {
        return serviceIds.isEmpty();
    }
}
