package com.autopartco.backend.shared.events;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Evento de dominio: cambió algo que puede alterar los listados publicados del marketplace (producto, inventario,
 * precio, promoción, sucursal, estado del almacén o su calificación). Lo publican los servicios dentro de su
 * transacción; {@code core.search} lo escucha tras el commit y reindexa solo el alcance indicado. Vive en
 * {@code shared} para que cualquier módulo lo publique sin depender de {@code core.search}.
 *
 * @param productIds   productos afectados
 * @param inventoryIds listados ({@code inventory.id}) afectados cuando no se conoce el producto
 * @param branchIds    sucursales afectadas (ubicación, estado)
 * @param storeIds     almacenes afectados (estado, calificación, promociones)
 * @param everything   reindexar todo (p. ej. promoción de plataforma)
 */
public record ListingsChanged(
        Set<UUID> productIds,
        Set<UUID> inventoryIds,
        Set<UUID> branchIds,
        Set<UUID> storeIds,
        boolean everything
) {
    public ListingsChanged {
        productIds = copy(productIds);
        inventoryIds = copy(inventoryIds);
        branchIds = copy(branchIds);
        storeIds = copy(storeIds);
    }

    public static ListingsChanged ofProducts(Collection<UUID> productIds) {
        return new ListingsChanged(Set.copyOf(nonNull(productIds)), Set.of(), Set.of(), Set.of(), false);
    }

    public static ListingsChanged ofProduct(UUID productId) {
        return ofProducts(productId == null ? Set.of() : Set.of(productId));
    }

    public static ListingsChanged ofInventory(Collection<UUID> inventoryIds) {
        return new ListingsChanged(Set.of(), Set.copyOf(nonNull(inventoryIds)), Set.of(), Set.of(), false);
    }

    public static ListingsChanged ofBranch(UUID branchId) {
        return new ListingsChanged(Set.of(), Set.of(), branchId == null ? Set.of() : Set.of(branchId), Set.of(),
                false);
    }

    public static ListingsChanged ofStore(UUID storeId) {
        return new ListingsChanged(Set.of(), Set.of(), Set.of(), storeId == null ? Set.of() : Set.of(storeId),
                false);
    }

    public static ListingsChanged ofEverything() {
        return new ListingsChanged(Set.of(), Set.of(), Set.of(), Set.of(), true);
    }

    public boolean isEmpty() {
        return !everything && productIds.isEmpty() && inventoryIds.isEmpty() && branchIds.isEmpty()
                && storeIds.isEmpty();
    }

    private static Set<UUID> copy(Set<UUID> ids) {
        return ids == null ? Set.of() : Set.copyOf(nonNull(ids));
    }

    private static Set<UUID> nonNull(Collection<UUID> ids) {
        return ids == null ? Set.of() : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
