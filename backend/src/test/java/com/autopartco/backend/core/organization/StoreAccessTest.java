package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;

@ExtendWith(MockitoExtension.class)
class StoreAccessTest {
    @Mock
    private AccountLookup accountLookup;

    @Mock
    private CollaboratorRepository collaboratorRepository;

    @Mock
    private StoreApplicationRepository applicationRepository;

    @InjectMocks
    private StoreAccess storeAccess;

    @Test
    void returnsPermissionCodesForTheAuthenticatedPersonAndStore() {
        UUID personId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(applicationRepository.existsByStore_Id(storeId)).thenReturn(false);
        AccountRef account = account(personId);
        when(accountLookup.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(collaboratorRepository.findPermissionCodes(personId, storeId))
                .thenReturn(List.of("BRANCH_READ", "PRODUCT_READ"));

        assertEquals(
                List.of("BRANCH_READ", "PRODUCT_READ"),
                storeAccess.permissionsFor(" Ana@Example.com ", storeId)
        );
    }

    @Test
    void rejectsActionsWithoutTheRequestedStorePermission() {
        UUID personId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(applicationRepository.existsByStore_Id(storeId)).thenReturn(false);
        AccountRef account = account(personId);
        when(accountLookup.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(collaboratorRepository.hasPermission(personId, storeId, "PRODUCT_DELETE"))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> storeAccess.requirePermission("ana@example.com", storeId, "PRODUCT_DELETE")
        );
    }

    private AccountRef account(UUID personId) {
        return new AccountRef(UUID.randomUUID(), personId, "ana@example.com", "Ana Pérez", "ACTIVE", "CUSTOMER", "Ana");
    }
}
