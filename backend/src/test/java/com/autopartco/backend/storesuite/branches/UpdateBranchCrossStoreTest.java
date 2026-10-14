package com.autopartco.backend.storesuite.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.organization.BranchService;
import com.autopartco.backend.core.organization.BranchUpdateRequest;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.internal.BranchRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.location.LocationRepository;
import com.autopartco.backend.shared.persistence.StateRepository;

/** IDOR entre almacenes: con permiso en el almacén A no se puede modificar una sucursal del almacén B. */
@ExtendWith(MockitoExtension.class)
class UpdateBranchCrossStoreTest {
    private static final String EMAIL = "staff-a@example.com";
    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID BRANCH_OF_B = UUID.randomUUID();

    @Mock
    private StoreAccess storeAccess;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StateRepository stateRepository;

    private UpdateBranch useCase() {
        return new UpdateBranch(storeAccess,
                new BranchService(branchRepository, locationRepository, storeRepository, stateRepository));
    }

    private static BranchUpdateRequest renameRequest() {
        return new BranchUpdateRequest("Robada", null, null);
    }

    @Test
    void branchOfAnotherStoreThroughOwnStoreIsNotFound() {
        when(storeRepository.findById(STORE_A)).thenReturn(Optional.of(new Store()));
        when(branchRepository.findInStore(BRANCH_OF_B, STORE_A)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> useCase().execute(STORE_A, BRANCH_OF_B, EMAIL, renameRequest()));

        assertEquals("BRANCH_NOT_FOUND", exception.getCode());
        verify(storeAccess).requirePermission(EMAIL, STORE_A, StorePermissionCodes.BRANCH_UPDATE);
        verify(branchRepository, never()).nameUsedByOther(any(), anyString(), any());
        verifyNoInteractions(locationRepository);
    }

    @Test
    void anotherStoreWithoutPermissionIsForbiddenBeforeLookingUpTheBranch() {
        doThrow(new AccessDeniedException("Store permission denied: " + StorePermissionCodes.BRANCH_UPDATE))
                .when(storeAccess).requirePermission(EMAIL, STORE_B, StorePermissionCodes.BRANCH_UPDATE);

        assertThrows(AccessDeniedException.class,
                () -> useCase().execute(STORE_B, BRANCH_OF_B, EMAIL, renameRequest()));

        verifyNoInteractions(branchRepository, storeRepository, locationRepository, stateRepository);
    }
}
