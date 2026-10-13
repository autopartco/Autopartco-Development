package com.autopartco.backend.core.organization;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;

import lombok.AllArgsConstructor;

/**
 * Permisos de una cuenta sobre un almacén. La usan los casos de uso de los productos antes de invocar
 * {@code core}. Deniega si el almacén tiene solicitud y esta no está {@code ACTIVE}.
 */
@Service
@AllArgsConstructor
public class StoreAccess {
    private final AccountLookup accountLookup;
    private final CollaboratorRepository collaboratorRepository;
    private final StoreApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public List<String> permissionsFor(String email, UUID storeId) {
        if (!applicationAllowsAccess(storeId)) {
            return List.of();
        }
        var account = findAccount(email);
        return collaboratorRepository.findPermissionCodes(account.personId(), storeId);
    }

    @Transactional(readOnly = true)
    public void requirePermission(String email, UUID storeId, String permissionCode) {
        var account = findAccount(email);
        if (!applicationAllowsAccess(storeId) || !collaboratorRepository.hasPermission(
                account.personId(),
                storeId,
                permissionCode
        )) {
            throw new AccessDeniedException("Store permission denied: " + permissionCode);
        }
    }

    private boolean applicationAllowsAccess(UUID storeId) {
        return !applicationRepository.existsByStore_Id(storeId)
                || applicationRepository.existsByStore_IdAndStatus(
                        storeId,
                        StoreApplicationStatus.ACTIVE
                );
    }

    private AccountRef findAccount(String email) {
        return accountLookup.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Authenticated account was not found."));
    }
}
