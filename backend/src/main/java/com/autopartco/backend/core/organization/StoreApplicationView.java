package com.autopartco.backend.core.organization;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de almacén con sus documentos. Los planes van como ID: los productos los resuelven con
 * {@code billing.PlanService} (organization no puede depender de billing, §4.2).
 */
public record StoreApplicationView(
        UUID applicationId,
        UUID storeId,
        String businessName,
        String nit,
        String businessEmail,
        String applicantEmail,
        LegalType legalType,
        StoreApplicationStatus status,
        UUID planId,
        UUID selectedPlanId,
        List<Document> documents,
        String reviewNotes,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        Reviewer reviewer
) {
    /** Cuenta que revisó la solicitud; {@code null} en la vista si no hay revisor o la cuenta ya no existe. */
    public record Reviewer(UUID accountId, String name, String email) {
    }

    public record Document(
            UUID id,
            ApplicationDocumentType type,
            String filename,
            String contentType,
            long sizeBytes
    ) {
    }
}
