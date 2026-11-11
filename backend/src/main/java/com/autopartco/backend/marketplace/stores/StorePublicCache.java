package com.autopartco.backend.marketplace.stores;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.autopartco.backend.shared.cache.AppCaches;
import com.autopartco.backend.shared.cache.ResilientCacheManager;
import com.autopartco.backend.shared.cache.TypedCache;
import com.autopartco.backend.shared.events.ListingsChanged;
import com.autopartco.backend.shared.events.StoreProfileChanged;

import tools.jackson.core.type.TypeReference;

/**
 * Cachés {@code store:profile} y {@code store:branches} (5 min por {@code storeId}; Redis si está configurado). Tras el
 * commit se invalidan con {@link StoreProfileChanged} (perfil, marca, servicios, ajustes) y con
 * {@link ListingsChanged}: almacenes indicados (estado, calificación, promociones) o todo si cambió una sucursal o el
 * catálogo entero. Lo que no publica evento (conteo de productos, {@code openNow} de las sucursales) caduca con el TTL.
 * Solo guarda respuestas públicas (sin datos de la sesión).
 */
@Component
public class StorePublicCache {
    static final StorePublicCache NONE = new StorePublicCache(null, null);

    private final TypedCache<StorePublicProfile> profiles;
    private final TypedCache<List<PublicBranch>> branches;

    @Autowired
    public StorePublicCache(ResilientCacheManager caches) {
        this(caches.typed(AppCaches.STORE_PROFILE, StorePublicProfile.class),
                caches.typed(AppCaches.STORE_BRANCHES, new TypeReference<List<PublicBranch>>() {
                }.getType()));
    }

    private StorePublicCache(TypedCache<StorePublicProfile> profiles, TypedCache<List<PublicBranch>> branches) {
        this.profiles = profiles;
        this.branches = branches;
    }

    StorePublicProfile profile(UUID storeId, Supplier<StorePublicProfile> loader) {
        return profiles == null ? loader.get() : profiles.get(storeId.toString(), loader);
    }

    List<PublicBranch> branches(UUID storeId, Supplier<List<PublicBranch>> loader) {
        return branches == null ? loader.get() : branches.get(storeId.toString(), () -> List.copyOf(loader.get()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStoreProfileChanged(StoreProfileChanged event) {
        if (event != null && event.storeId() != null) {
            evict(event.storeId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingsChanged(ListingsChanged event) {
        if (event == null || profiles == null) {
            return;
        }
        if (event.everything() || !event.branchIds().isEmpty()) {
            profiles.clear();
            branches.clear();
            return;
        }
        event.storeIds().forEach(this::evict);
    }

    private void evict(UUID storeId) {
        if (profiles != null) {
            profiles.evict(storeId.toString());
            branches.evict(storeId.toString());
        }
    }
}
