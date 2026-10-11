package com.autopartco.backend.store.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.store.repository.CollaboratorRepository;
import com.autopartco.backend.seller.entity.SellerApplicationStatus;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class StoreAccessService {
    private final AccountRepository accountRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final SellerApplicationRepository sellerApplicationRepository;

    @Transactional(readOnly = true)
    public List<String> permissionsFor(String email, UUID storeId) {
        if (!applicationAllowsAccess(storeId)) {
            return List.of();
        }
        var account = findAccount(email);
        return collaboratorRepository.findPermissionCodes(account.getPerson().getId(), storeId);
    }

    @Transactional(readOnly = true)
    public void requirePermission(String email, UUID storeId, String permissionCode) {
        var account = findAccount(email);
        if (!applicationAllowsAccess(storeId) || !collaboratorRepository.hasPermission(
                account.getPerson().getId(),
                storeId,
                permissionCode
        )) {
            throw new AccessDeniedException("Store permission denied: " + permissionCode);
        }
    }

    private boolean applicationAllowsAccess(UUID storeId) {
        return !sellerApplicationRepository.existsByStore_Id(storeId)
                || sellerApplicationRepository.existsByStore_IdAndStatus(
                        storeId,
                        SellerApplicationStatus.ACTIVE
                );
    }

    private com.autopartco.backend.auth.entity.Account findAccount(String email) {
        return accountRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Authenticated account was not found."));
    }
}
