package com.autopartco.backend.storesuite.onboarding;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.billing.PlanResponse;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.identity.PreparedRegistration;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

/**
 * Solicitud de almacén: con cuenta nueva (registro público de vendedor) o con una cuenta de cliente existente.
 * Orquesta identity (cuenta), billing (plan elegible) y organization (almacén, solicitud y documentos) en una sola
 * transacción; si falla el correo de verificación, borra los documentos subidos.
 */
@Service
@AllArgsConstructor
public class SubmitStoreApplication {
    private final AccountProvisioning accountProvisioning;
    private final AccountLookup accountLookup;
    private final PlanService planService;
    private final StoreApplications storeApplications;

    @Transactional
    public SellerSignupResponse register(
            SellerSignupRequest request,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        validateDocuments(request.legalType(), documents);
        PlanResponse plan = planService.requireSelectable(request.planId());
        PreparedRegistration prepared = accountProvisioning.registerSellerAccount(request.account());
        AccountRef account = accountLookup.findByEmail(prepared.response().email())
                .orElseThrow(() -> new NotFoundException(
                        "SELLER_ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta recién registrada."
                ));
        StoreApplications.Submitted created = storeApplications.submit(
                account.accountId(),
                account.email(),
                request.store(),
                request.legalType(),
                plan.id(),
                documents
        );
        try {
            accountProvisioning.sendSellerVerification(prepared);
        } catch (RuntimeException exception) {
            storeApplications.discardUploadedFiles(created.uploadedPaths(), exception);
            throw exception;
        }

        return new SellerSignupResponse(
                prepared.response(),
                created.store(),
                created.applicationId(),
                created.status(),
                plan.code()
        );
    }

    @Transactional
    public StoreApplicationCreatedResponse applyForExistingCustomer(
            StoreApplicationRequest request,
            Map<ApplicationDocumentType, MultipartFile> documents,
            String email
    ) {
        validateDocuments(request.legalType(), documents);
        AccountRef account = accountLookup.requireByEmail(email.trim());
        if (!StateCodes.ACTIVE.equals(account.stateCode())) {
            throw new BadRequestException("SELLER_EMAIL_NOT_VERIFIED", "Verifica tu correo antes de solicitar ser vendedor.");
        }
        if (!"CUSTOMER".equals(account.accountTypeCode())) {
            throw new BadRequestException("SELLER_APPLICATION_CUSTOMER_ONLY", "Solo una cuenta de cliente puede solicitar ser vendedor.");
        }
        storeApplications.requireNoOpenApplication(account.accountId());
        PlanResponse plan = planService.requireSelectable(request.planId());
        StoreApplications.Submitted created = storeApplications.submit(
                account.accountId(),
                account.email(),
                request.store(),
                request.legalType(),
                plan.id(),
                documents
        );
        return new StoreApplicationCreatedResponse(
                created.applicationId(),
                created.store().id(),
                created.status(),
                plan.code()
        );
    }

    private void validateDocuments(
            LegalType legalType,
            Map<ApplicationDocumentType, MultipartFile> documents
    ) {
        Set<ApplicationDocumentType> required = legalType == LegalType.NATURAL_PERSON
                ? EnumSet.of(ApplicationDocumentType.IDENTITY_DOCUMENT, ApplicationDocumentType.RUT)
                : EnumSet.of(
                        ApplicationDocumentType.RUT,
                        ApplicationDocumentType.LEGAL_REPRESENTATIVE_ID,
                        ApplicationDocumentType.CERTIFICATE_OF_EXISTENCE
                );
        if (documents == null
                || !documents.keySet().equals(required)
                || documents.values().stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new BadRequestException(
                    "SELLER_DOCUMENTS_INCOMPLETE",
                    legalType == LegalType.NATURAL_PERSON
                            ? "Adjunta la cédula y el RUT."
                            : "Adjunta el RUT, el certificado de existencia y representación legal y la cédula del representante."
            );
        }
    }
}
