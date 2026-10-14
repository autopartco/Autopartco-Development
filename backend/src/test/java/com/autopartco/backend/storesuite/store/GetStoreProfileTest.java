package com.autopartco.backend.storesuite.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreProfileResponse;
import com.autopartco.backend.core.organization.StoreService;

@ExtendWith(MockitoExtension.class)
class GetStoreProfileTest {
    @Mock
    private StoreAccess storeAccess;

    @Mock
    private StoreService storeService;

    @InjectMocks
    private GetStoreProfile getStoreProfile;

    @Test
    void returnsProfileWhenThePersonCanReadTheStore() {
        UUID storeId = UUID.randomUUID();
        StoreProfileResponse profile = new StoreProfileResponse(
                storeId, "Repuestos La 30", "900123456", "ventas@la30.co", "ACTIVE", "Repuestos de frenos", null, null
        );
        when(storeService.profile(storeId)).thenReturn(profile);

        assertEquals(profile, getStoreProfile.execute(storeId, "ana@example.com"));
    }

    @Test
    void rejectsProfileOfAStoreTheCallerDoesNotBelongTo() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess)
                .requirePermission(eq("ana@example.com"), eq(storeId), eq(StorePermissionCodes.STORE_READ));

        assertThrows(AccessDeniedException.class, () -> getStoreProfile.execute(storeId, "ana@example.com"));
        verifyNoInteractions(storeService);
    }
}
