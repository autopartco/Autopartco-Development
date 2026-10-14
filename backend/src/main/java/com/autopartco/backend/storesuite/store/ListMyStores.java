package com.autopartco.backend.storesuite.store;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.MyStoreMembership;
import com.autopartco.backend.core.organization.StoreAccess;

import lombok.AllArgsConstructor;

/** Tiendas donde la persona es colaborador activo. No requiere permiso de tienda. */
@Service
@AllArgsConstructor
public class ListMyStores {
    private final StoreAccess storeAccess;

    @Transactional(readOnly = true)
    public List<MyStoreResponse> execute(String email) {
        return storeAccess.storesOf(email).stream()
                .map(ListMyStores::toResponse)
                .toList();
    }

    private static MyStoreResponse toResponse(MyStoreMembership membership) {
        return new MyStoreResponse(
                membership.storeId(),
                membership.businessName(),
                null,
                membership.applicationStatus(),
                membership.owner(),
                membership.roleNames(),
                null
        );
    }
}
