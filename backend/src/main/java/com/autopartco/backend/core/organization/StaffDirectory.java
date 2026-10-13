package com.autopartco.backend.core.organization;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.CollaboratorRepository;

import lombok.AllArgsConstructor;

/**
 * Directorio de colaboradores de un almacén. Por ahora solo expone el conteo de activos que usa
 * {@code billing.PlanEntitlements}; el resto de operaciones llega en la fase 6.
 */
@Service
@AllArgsConstructor
public class StaffDirectory {
    private static final String ACTIVE_STATE = "ACTIVE";

    private final CollaboratorRepository collaboratorRepository;

    @Transactional(readOnly = true)
    public long countActive(UUID storeId) {
        return collaboratorRepository.countByStore_IdAndState_Code(storeId, ACTIVE_STATE);
    }
}
