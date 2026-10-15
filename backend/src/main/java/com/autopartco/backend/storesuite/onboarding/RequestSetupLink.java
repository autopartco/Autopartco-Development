package com.autopartco.backend.storesuite.onboarding;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreApplications;

import java.util.UUID;

import lombok.AllArgsConstructor;

/**
 * Reenvía el enlace de configuración del almacén: con la sesión (dueña de la solicitud) o, sin sesión, a partir
 * del token anterior. Cada envío emite un token nuevo que invalida el anterior. El correo sale después del commit y
 * en otro hilo ({@link SetupLinkMailListener}), así la respuesta no depende de si el token existía.
 */
@Service
@AllArgsConstructor
public class RequestSetupLink {
    private final StoreApplications storeApplications;
    private final ApplicationEventPublisher events;

    /** Con sesión: errores 404, 409 y 429 de {@link StoreApplications#reissueOnboardingTokenForOwner}. */
    @Transactional
    public void forOwner(UUID applicationId, String email) {
        publish(storeApplications.reissueOnboardingTokenForOwner(applicationId, email));
    }

    /** Sin sesión: nunca falla ni revela si el token existe; si no corresponde a nada, no hace nada. */
    @Transactional
    public void byToken(String rawToken) {
        storeApplications.reissueOnboardingTokenByToken(rawToken).ifPresent(this::publish);
    }

    private void publish(StoreApplications.SetupLink link) {
        events.publishEvent(new SetupLinkIssued(link.applicantEmail(), link.applicantGivenName(), link.onboardingToken()));
    }
}
