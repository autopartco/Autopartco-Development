package com.autopartco.backend.storesuite.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.organization.MyStoreMembership;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreApplicationStatus;

@ExtendWith(MockitoExtension.class)
class ListMyStoresTest {
    @Mock
    private StoreAccess storeAccess;

    @InjectMocks
    private ListMyStores listMyStores;

    @Test
    void returnsEmptyListWhenThePersonHasNoStores() {
        when(storeAccess.storesOf("ana@example.com")).thenReturn(List.of());

        assertTrue(listMyStores.execute("ana@example.com").isEmpty());
    }

    @Test
    void mapsMembershipsWithoutInventingLogoOrSubscription() {
        UUID storeId = UUID.randomUUID();
        when(storeAccess.storesOf("ana@example.com")).thenReturn(List.of(new MyStoreMembership(
                storeId,
                "Repuestos La 30",
                StoreApplicationStatus.CONFIGURATION_PENDING,
                true,
                List.of("Propietario")
        )));

        List<MyStoreResponse> stores = listMyStores.execute("ana@example.com");

        assertEquals(1, stores.size());
        MyStoreResponse store = stores.getFirst();
        assertEquals(storeId, store.storeId());
        assertEquals("Repuestos La 30", store.businessName());
        assertEquals(StoreApplicationStatus.CONFIGURATION_PENDING, store.applicationStatus());
        assertTrue(store.isOwner());
        assertEquals(List.of("Propietario"), store.roleNames());
        assertNull(store.logoUrl());
        assertNull(store.subscription());
    }
}
