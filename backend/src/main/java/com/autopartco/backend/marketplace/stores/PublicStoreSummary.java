package com.autopartco.backend.marketplace.stores;

import java.util.UUID;

import com.autopartco.backend.core.organization.StoreResponse;

/** Resumen público de un almacén publicado. Nunca incluye NIT ni correo del almacén. */
public record PublicStoreSummary(UUID id, String businessName, String state) {
    static PublicStoreSummary of(StoreResponse store) {
        return new PublicStoreSummary(store.id(), store.businessName(), store.state());
    }
}
