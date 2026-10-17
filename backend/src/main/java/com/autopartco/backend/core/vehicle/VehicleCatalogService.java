package com.autopartco.backend.core.vehicle;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.vehicle.internal.VehicleCatalogRepository;
import com.autopartco.backend.core.vehicle.internal.VehicleRefView;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/** Catálogo de vehículos en cascada Marca -> Modelo -> Versión, ordenado por nombre. */
@Service
@AllArgsConstructor
public class VehicleCatalogService {
    private final VehicleCatalogRepository repository;

    @Transactional(readOnly = true)
    public List<VehicleOption> makes() {
        return repository.findMakes().stream().map(v -> new VehicleOption(v.getId(), v.getName())).toList();
    }

    /** Modelos de una marca; {@code MAKE_NOT_FOUND} si la marca no existe. */
    @Transactional(readOnly = true)
    public List<VehicleOption> models(UUID makeId) {
        if (!repository.makeExists(makeId)) {
            throw new NotFoundException("MAKE_NOT_FOUND", "La marca no existe.");
        }
        return repository.findModels(makeId).stream().map(v -> new VehicleOption(v.getId(), v.getName())).toList();
    }

    /** Versiones de un modelo; {@code MODEL_NOT_FOUND} si el modelo no existe. */
    @Transactional(readOnly = true)
    public List<VersionOption> versions(UUID modelId) {
        if (!repository.modelExists(modelId)) {
            throw new NotFoundException("MODEL_NOT_FOUND", "El modelo no existe.");
        }
        return repository.findVersions(modelId).stream().map(v -> new VersionOption(v.getId(), v.getName())).toList();
    }

    /**
     * Resuelve en lote (tres consultas como máximo) marcas, modelos y versiones vigentes, con su padre. Sirve para
     * validar la coherencia de reglas y para nombrarlas. Colecciones vacías no consultan.
     */
    @Transactional(readOnly = true)
    public VehicleRefs resolve(Collection<UUID> makeIds, Collection<UUID> modelIds, Collection<UUID> versionIds) {
        Map<UUID, String> makes = new HashMap<>();
        if (!makeIds.isEmpty()) {
            repository.findMakesByIds(Set.copyOf(makeIds)).forEach(v -> makes.put(v.getId(), v.getName()));
        }
        return new VehicleRefs(makes, refs(modelIds, repository::findModelsByIds),
                refs(versionIds, repository::findVersionsByIds));
    }

    private static Map<UUID, VehicleRefs.Ref> refs(
            Collection<UUID> ids, Function<Collection<UUID>, List<VehicleRefView>> query) {
        Map<UUID, VehicleRefs.Ref> result = new HashMap<>();
        if (!ids.isEmpty()) {
            query.apply(Set.copyOf(ids)).forEach(v ->
                    result.put(v.getId(), new VehicleRefs.Ref(v.getId(), v.getParentId(), v.getName())));
        }
        return result;
    }
}
