package com.autopartco.backend.storesuite.store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.StoreApplicationStatus;

/** Tienda de la persona autenticada (BL-B3). {@code subscription} queda nulo hasta que exista SA-B2. */
public record MyStoreResponse(
        UUID storeId,
        String businessName,
        String logoUrl,
        StoreApplicationStatus applicationStatus,
        boolean isOwner,
        List<String> roleNames,
        MyStoreSubscription subscription
) {

    public record MyStoreSubscription(
            String status,
            String planCode,
            String planName,
            LocalDateTime validThrough
    ) {
    }
}
