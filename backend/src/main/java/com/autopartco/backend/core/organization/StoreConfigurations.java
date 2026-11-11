package com.autopartco.backend.core.organization;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Acceso a {@code store.configuration} (jsonb) para {@code core.settings}. Solo escribe las claves de primer nivel
 * que recibe: {@code description} y {@code publicProfile} (perfil, {@link StoreService}) se conservan.
 */
@Service
@AllArgsConstructor
public class StoreConfigurations {
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> read(UUID storeId) {
        return copy(storeRepository.findById(storeId).orElseThrow(StoreConfigurations::notFound).getConfiguration());
    }

    /** Lee la configuración bloqueando la fila del almacén hasta el final de la transacción del llamador. */
    @Transactional
    public Map<String, Object> lockForUpdate(UUID storeId) {
        return copy(storeRepository.lockById(storeId).orElseThrow(StoreConfigurations::notFound).getConfiguration());
    }

    /** Reemplaza solo las claves de {@code sections}; el resto de la configuración queda igual. */
    @Transactional
    public void putSections(UUID storeId, Map<String, Object> sections) {
        Store store = storeRepository.findById(storeId).orElseThrow(StoreConfigurations::notFound);
        Map<String, Object> configuration = copy(store.getConfiguration());
        configuration.putAll(sections);
        store.setConfiguration(configuration);
        storeRepository.save(store);
    }

    private static Map<String, Object> copy(Map<String, Object> configuration) {
        return configuration == null ? new HashMap<>() : new HashMap<>(configuration);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén.");
    }
}
