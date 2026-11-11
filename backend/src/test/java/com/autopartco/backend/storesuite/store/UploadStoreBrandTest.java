package com.autopartco.backend.storesuite.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StoreFiles;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.organization.StoreProfileResponse;
import com.autopartco.backend.core.organization.StoreService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.PayloadTooLargeException;

class UploadStoreBrandTest {
    private static final String EMAIL = "ana@la30.co";

    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final StoreService storeService = mock(StoreService.class);
    private final StoreFiles storeFiles = mock(StoreFiles.class);
    private final UploadStoreBrand useCase = new UploadStoreBrand(storeAccess, storeService, storeFiles,
            mock(org.springframework.context.ApplicationEventPublisher.class));
    private final UUID storeId = UUID.randomUUID();

    private static MockMultipartFile image(int bytes) {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[bytes]);
    }

    @Test
    void uploadsSavesPathAndDeletesPreviousFile() {
        MockMultipartFile file = image(1024);
        StoreProfileResponse profile = mock(StoreProfileResponse.class);
        when(storeFiles.uploadBrandAsset(storeId, "logo", file)).thenReturn("stores/x/brand/logo/new.png");
        when(storeService.replaceBrandAsset(storeId, "logo", "stores/x/brand/logo/new.png"))
                .thenReturn("stores/x/brand/logo/old.png");
        when(storeService.profile(storeId)).thenReturn(profile);

        assertThat(useCase.upload(storeId, EMAIL, "logo", file)).isSameAs(profile);
        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE);
        verify(storeFiles).delete("stores/x/brand/logo/old.png");
    }

    @Test
    void logoOverTwoMegabytesIsRejectedBeforeUploading() {
        assertThatThrownBy(() -> useCase.upload(storeId, EMAIL, "logo", image(2 * 1024 * 1024 + 1)))
                .isInstanceOfSatisfying(PayloadTooLargeException.class,
                        error -> assertThat(error.getCode()).isEqualTo("BRAND_IMAGE_TOO_LARGE"));
        verify(storeFiles, never()).uploadBrandAsset(any(), anyString(), any());
    }

    @Test
    void bannerAcceptsUpToFourMegabytes() {
        MockMultipartFile file = image(3 * 1024 * 1024);
        when(storeFiles.uploadBrandAsset(storeId, "banner", file)).thenReturn("p");

        useCase.upload(storeId, EMAIL, "banner", file);

        verify(storeService).replaceBrandAsset(storeId, "banner", "p");
    }

    @Test
    void withoutPermissionNothingIsUploaded() {
        doThrow(new AccessDeniedException("denied"))
                .when(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE);

        assertThatThrownBy(() -> useCase.upload(storeId, EMAIL, "logo", image(10)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storeFiles, storeService);
    }

    @Test
    void unknownTypeIsNotFound() {
        assertThatThrownBy(() -> useCase.upload(storeId, EMAIL, "avatar", image(10)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(storeAccess, storeFiles);
    }

    @Test
    void uploadedFileIsDeletedWhenSavingFails() {
        MockMultipartFile file = image(10);
        when(storeFiles.uploadBrandAsset(storeId, "logo", file)).thenReturn("new");
        when(storeService.replaceBrandAsset(storeId, "logo", "new"))
                .thenThrow(new NotFoundException("STORE_NOT_FOUND", "x"));

        assertThatThrownBy(() -> useCase.upload(storeId, EMAIL, "logo", file)).isInstanceOf(NotFoundException.class);
        verify(storeFiles).delete("new");
    }

    @Test
    void removeClearsPathAndDeletesFile() {
        when(storeService.replaceBrandAsset(storeId, "banner", null)).thenReturn("old");

        useCase.remove(storeId, EMAIL, "banner");

        verify(storeFiles).delete("old");
    }
}
