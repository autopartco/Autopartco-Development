package com.autopartco.backend.operations.storeverification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.StoreApplicationView;

/**
 * Solicitud de almacén para root. Mismo JSON que {@code storesuite.onboarding}
 * {@code StoreApplicationResponse}: los productos no se importan entre sí y organization no puede resolver planes.
 */
public record StoreApplicationResponse(
        UUID applicationId,
        UUID storeId,
        String businessName,
        String nit,
        String businessEmail,
        String applicantEmail,
        LegalType legalType,
        StoreApplicationStatus status,
        PlanResponse plan,
        PlanResponse selectedPlan,
        List<Document> documents,
        String reviewNotes,
        LocalDateTime createdAt
) {
    public record Document(
            UUID id,
            ApplicationDocumentType type,
            String filename,
            String contentType,
            long sizeBytes
    ) {
    }

    static StoreApplicationResponse from(StoreApplicationView view, PlanService planService) {
        return new StoreApplicationResponse(
                view.applicationId(),
                view.storeId(),
                view.businessName(),
                view.nit(),
                view.businessEmail(),
                view.applicantEmail(),
                view.legalType(),
                view.status(),
                planService.plan(view.planId()),
                planService.plan(view.selectedPlanId()),
                view.documents().stream()
                        .map(document -> new Document(
                                document.id(),
                                document.type(),
                                document.filename(),
                                document.contentType(),
                                document.sizeBytes()
                        ))
                        .toList(),
                view.reviewNotes(),
                view.createdAt()
        );
    }
}
