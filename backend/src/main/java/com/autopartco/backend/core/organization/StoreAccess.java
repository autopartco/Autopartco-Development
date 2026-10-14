package com.autopartco.backend.core.organization;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;

import lombok.AllArgsConstructor;

/**
 * Permisos de una cuenta sobre un almacén. La usan los casos de uso de los productos antes de invocar
 * {@code core}. Deniega si el almacén tiene solicitud y esta no está {@code ACTIVE}.
 */
@Service
@AllArgsConstructor
public class StoreAccess {
    private static final String OWNER_ROLE = "OWNER";

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

    /** Tiendas donde la persona es colaborador activo. Solo deriva de {@code email}; no acepta más filtros. */
    @Transactional(readOnly = true)
    public List<MyStoreMembership> storesOf(String email) {
        var account = findAccount(email);
        var memberships = collaboratorRepository.findActiveMembershipsByPersonId(account.personId());
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> storeIds = memberships.stream().map(membership -> membership.getStore().getId()).toList();
        Map<UUID, StoreApplicationStatus> statuses = new HashMap<>();
        for (Object[] row : applicationRepository.findStatusesByStoreIds(storeIds)) {
            statuses.put((UUID) row[0], (StoreApplicationStatus) row[1]);
        }
        return memberships.stream()
                .map(membership -> {
                    UUID storeId = membership.getStore().getId();
                    List<StoreRole> roles = membership.getRoles().stream().toList();
                    return new MyStoreMembership(
                            storeId,
                            membership.getStore().getBusinessName(),
                            statuses.getOrDefault(storeId, StoreApplicationStatus.ACTIVE),
                            roles.stream().anyMatch(role -> OWNER_ROLE.equals(role.getCode())),
                            roles.stream().map(StoreRole::getName).sorted().toList()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireOwner(String email, UUID storeId) {
        var account = findAccount(email);
        if (!applicationAllowsAccess(storeId) || !collaboratorRepository.isOwner(account.personId(), storeId)) {
            throw new AccessDeniedException("Store owner required.");
        }
    }

    private boolean applicationAllowsAccess(UUID storeId) {
        return !applicationRepository.existsForStore(storeId)
                || applicationRepository.isActive(storeId);
    }

    private AccountRef findAccount(String email) {
        return accountLookup.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Authenticated account was not found."));
    }
}
