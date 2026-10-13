package com.autopartco.backend.store.service;

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
import com.autopartco.backend.store.repository.CollaboratorRepository;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;

@ExtendWith(MockitoExtension.class)
class StoreAccessServiceTest {
    @Mock
    private AccountLookup accountLookup;

    @Mock
    private CollaboratorRepository collaboratorRepository;

    @Mock
    private SellerApplicationRepository sellerApplicationRepository;

    @InjectMocks
    private StoreAccessService storeAccessService;

    @Test
    void returnsPermissionCodesForTheAuthenticatedPersonAndStore() {
        UUID personId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(sellerApplicationRepository.existsByStore_Id(storeId)).thenReturn(false);
        AccountRef account = account(personId);
        when(accountLookup.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(collaboratorRepository.findPermissionCodes(personId, storeId))
                .thenReturn(List.of("BRANCH_READ", "PRODUCT_READ"));

        assertEquals(
                List.of("BRANCH_READ", "PRODUCT_READ"),
                storeAccessService.permissionsFor(" Ana@Example.com ", storeId)
        );
    }

    @Test
    void rejectsActionsWithoutTheRequestedStorePermission() {
        UUID personId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(sellerApplicationRepository.existsByStore_Id(storeId)).thenReturn(false);
        AccountRef account = account(personId);
        when(accountLookup.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(account));
        when(collaboratorRepository.hasPermission(personId, storeId, "PRODUCT_DELETE"))
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> storeAccessService.requirePermission("ana@example.com", storeId, "PRODUCT_DELETE")
        );
    }

    private AccountRef account(UUID personId) {
        return new AccountRef(UUID.randomUUID(), personId, "ana@example.com", "Ana Pérez", "ACTIVE", "CUSTOMER");
    }
}
