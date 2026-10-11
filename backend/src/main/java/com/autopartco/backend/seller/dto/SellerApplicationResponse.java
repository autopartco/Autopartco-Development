package com.autopartco.backend.seller.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.seller.entity.SellerLegalType;

public record SellerApplicationResponse(
        UUID applicationId,
        UUID storeId,
        String businessName,
        String nit,
        String businessEmail,
        String applicantEmail,
        SellerLegalType legalType,
        SellerApplicationStatus status,
        SellerPlanResponse plan,
        SellerPlanResponse selectedPlan,
        List<Document> documents,
        String reviewNotes,
        LocalDateTime createdAt
) {
    public record Document(
            UUID id,
            SellerDocumentType type,
            String filename,
            String contentType,
            long sizeBytes
    ) {
    }
}
