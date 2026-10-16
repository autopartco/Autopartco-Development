package com.autopartco.backend.operations.storeverification;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.notification.SellerSetupMailer;
import com.autopartco.backend.core.organization.StoreApplicationReviewRequest;
import com.autopartco.backend.core.organization.StoreApplications;

import lombok.AllArgsConstructor;

/**
 * Revisión de una solicitud de almacén por root. Si se aprueba, envía al solicitante el enlace de configuración
 * ({@code /seller/setup}, sin token) dentro de la misma transacción.
 */
@Service
@AllArgsConstructor
public class ReviewStoreApplication {
    private final StoreApplications storeApplications;
    private final PlanService planService;
    private final SellerSetupMailer sellerSetupMailer;

    @Transactional
    public StoreApplicationResponse execute(
            UUID applicationId,
            String reviewerEmail,
            StoreApplicationReviewRequest request
    ) {
        StoreApplications.Reviewed reviewed = storeApplications.review(applicationId, reviewerEmail, request);
        if (reviewed.approved()) {
            sellerSetupMailer.send(reviewed.applicantEmail(), reviewed.applicantGivenName());
        }
        return StoreApplicationResponse.from(reviewed.application(), planService);
    }
}
