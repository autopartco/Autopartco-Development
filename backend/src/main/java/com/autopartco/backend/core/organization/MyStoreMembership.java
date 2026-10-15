package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.UUID;

/**
 * Tienda en la que una persona es colaborador activo. {@code applicationStatus} es {@code ACTIVE} cuando la tienda
 * no tiene solicitud, igual que {@link StoreAccess} la trata para permisos.
 */
public record MyStoreMembership(
        UUID storeId,
        String businessName,
        StoreApplicationStatus applicationStatus,
        boolean owner,
        List<String> roleNames
) {
}
