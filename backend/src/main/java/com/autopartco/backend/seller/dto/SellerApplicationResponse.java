package com.autopartco.backend.seller.dto;

import com.autopartco.backend.core.billing.PlanResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.organization.LegalType;

public record SellerApplicationResponse(
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
}
