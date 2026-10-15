package com.autopartco.backend.storesuite.store;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.StoreProfileResponse;
import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreStatusResponse;
import com.autopartco.backend.core.organization.StoreUpdateRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/stores")
@AllArgsConstructor
public class StoreProfileController {
    private final UpdateStoreProfile updateStoreProfile;
    private final GetStoreStatus getStoreStatus;
    private final ListMyStorePermissions listMyStorePermissions;
    private final GetStoreProfile getStoreProfile;

    @GetMapping("/{storeId}/profile")
    public StoreProfileResponse profile(@PathVariable UUID storeId, Authentication authentication) {
        return getStoreProfile.execute(storeId, authentication.getName());
    }

    @PatchMapping("/{storeId}")
    public StoreResponse update(
            @PathVariable UUID storeId,
            @Valid @RequestBody StoreUpdateRequest request,
            Authentication authentication
    ) {
        return updateStoreProfile.execute(storeId, authentication.getName(), request);
    }

    @GetMapping("/{storeId}/status")
    public StoreStatusResponse status(@PathVariable UUID storeId, Authentication authentication) {
        return getStoreStatus.execute(storeId, authentication.getName());
    }

    @GetMapping("/{storeId}/my-permissions")
    public List<String> myPermissions(@PathVariable UUID storeId, Authentication authentication) {
        return listMyStorePermissions.execute(storeId, authentication.getName());
    }
}
