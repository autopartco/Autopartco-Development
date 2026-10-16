package com.autopartco.backend.storesuite.onboarding;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreApplications;

import java.util.UUID;

import lombok.AllArgsConstructor;

/**
 * Reenvía a la dueña de la solicitud (con sesión) el correo con el enlace {@code /seller/setup}, que no lleva token.
 * El correo sale después del commit y en otro hilo ({@link SetupLinkMailListener}).
 */
@Service
@AllArgsConstructor
public class RequestSetupLink {
    private final StoreApplications storeApplications;
    private final ApplicationEventPublisher events;

    /** Errores 404, 409 y 429 de {@link StoreApplications#requestSetupLinkForOwner}. */
    @Transactional
    public void forOwner(UUID applicationId, String email) {
        StoreApplications.SetupLink link = storeApplications.requestSetupLinkForOwner(applicationId, email);
        events.publishEvent(new SetupLinkIssued(link.applicantEmail(), link.applicantGivenName()));
    }
}
