package com.autopartco.backend.core.organization;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/** Fachada de las solicitudes de almacén (vendedor). */
@Service
@AllArgsConstructor
public class StoreApplications {
    private final StoreApplicationRepository applicationRepository;

    /** Resumen de la solicitud del almacén; si no existe, {@code SELLER_APPLICATION_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public StoreApplicationSummary summaryOf(UUID storeId) {
        StoreApplication application = applicationRepository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_APPLICATION_NOT_FOUND",
                        "No se encontró la suscripción de vendedor para este almacén."
                ));
        return new StoreApplicationSummary(
                application.getStore().getBusinessName(),
                application.getStore().getState().getCode(),
                application.getConfigurationCompletedAt() == null
        );
    }
}
