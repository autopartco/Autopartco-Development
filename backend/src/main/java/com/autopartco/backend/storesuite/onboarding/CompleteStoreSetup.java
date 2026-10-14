package com.autopartco.backend.storesuite.onboarding;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.billing.Subscriptions;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.SessionIssuer;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreFiles;

import lombok.AllArgsConstructor;

/**
 * Cierra el onboarding con el token de configuración (público): sube la marca, activa el almacén, inicia la
 * prueba del plan, pasa la cuenta a {@code STORE_MANAGER} y abre sesión. Si algo falla, borra la marca subida.
 */
@Service
@AllArgsConstructor
public class CompleteStoreSetup {
    private final StoreApplications storeApplications;
    private final StoreFiles storeFiles;
    private final Subscriptions subscriptions;
    private final PlanService planService;
    private final AccountProvisioning accountProvisioning;
    private final SessionIssuer sessionIssuer;

    @Transactional
    public StoreSetupResponse execute(
            String rawToken,
            String businessDescription,
            MultipartFile logo,
            MultipartFile banner,
            String ipAddress,
            String userAgent
    ) {
        StoreApplications.PendingSetup setup = storeApplications.lockForSetup(rawToken);
        UUID storeId = setup.storeId();
        String newLogoPath = null;
        String newBannerPath = null;
        try {
            if (logo != null && !logo.isEmpty()) {
                newLogoPath = storeFiles.uploadBrandAsset(storeId, "logo", logo);
            }
            if (banner != null && !banner.isEmpty()) {
                newBannerPath = storeFiles.uploadBrandAsset(storeId, "banner", banner);
            }
            storeApplications.configureStore(storeId, newLogoPath, newBannerPath, businessDescription);
            LocalDateTime setupAt = LocalDateTime.now();
            LocalDateTime trialEndsAt = subscriptions.startTrial(storeId, setupAt);
            StoreApplicationStatus status = storeApplications.completeConfiguration(setup.applicationId(), setupAt);
            UUID applicantId = setup.applicantAccountId();
            accountProvisioning.promoteToStoreManager(applicantId);
            LoginResponse tokens = sessionIssuer.issue(applicantId, ipAddress, userAgent);
            return new StoreSetupResponse(
                    storeId,
                    status.name(),
                    trialEndsAt,
                    planService.recommended(),
                    tokens
            );
        } catch (RuntimeException exception) {
            cleanup(newLogoPath, exception);
            cleanup(newBannerPath, exception);
            throw exception;
        }
    }

    private void cleanup(String path, RuntimeException originalException) {
        if (path == null) {
            return;
        }
        try {
            storeFiles.delete(path);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
