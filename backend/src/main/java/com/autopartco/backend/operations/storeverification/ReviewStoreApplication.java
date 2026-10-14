package com.autopartco.backend.operations.storeverification;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.organization.StoreApplicationReviewRequest;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

/**
 * Revisión de una solicitud de almacén por root. Si se aprueba, envía al solicitante el enlace de configuración
 * ({@code /seller/setup?token=...}) dentro de la misma transacción.
 */
@Service
@AllArgsConstructor
public class ReviewStoreApplication {
    private final StoreApplications storeApplications;
    private final PlanService planService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Transactional
    public StoreApplicationResponse execute(
            UUID applicationId,
            String reviewerEmail,
            StoreApplicationReviewRequest request
    ) {
        StoreApplications.Reviewed reviewed = storeApplications.review(applicationId, reviewerEmail, request);
        if (reviewed.onboardingToken() != null) {
            String setupLink = UriComponentsBuilder
                    .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                    .path("/seller/setup")
                    .queryParam("token", reviewed.onboardingToken())
                    .build()
                    .toUriString();
            emailService.sendSellerOnboarding(
                    reviewed.applicantEmail(),
                    reviewed.applicantGivenName(),
                    setupLink
            );
        }
        return StoreApplicationResponse.from(reviewed.application(), planService);
    }
}
