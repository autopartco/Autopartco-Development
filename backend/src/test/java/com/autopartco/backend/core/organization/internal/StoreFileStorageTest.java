package com.autopartco.backend.core.organization.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.autopartco.backend.shared.storage.SupabaseStorageProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

@ExtendWith(MockitoExtension.class)
class StoreFileStorageTest {
    @Mock
    private SupabaseStorageService storageService;

    private final SupabaseStorageProperties properties =
            new SupabaseStorageProperties("https://supabase.example", "service-key", "products", "bussniess");

    private StoreFileStorage fileService;

    @BeforeEach
    void setUp() {
        fileService = new StoreFileStorage(storageService, properties);
    }

    @Test
    void rejectsContentThatDoesNotMatchItsDeclaredDocumentType() {
        MockMultipartFile file = new MockMultipartFile(
                "rut",
                "rut.pdf",
                "application/pdf",
                "not a pdf".getBytes()
        );

        assertThrows(
                BadRequestException.class,
                () -> fileService.uploadDocument(UUID.randomUUID(), ApplicationDocumentType.RUT, file)
        );
    }

    @Test
    void uploadsDocumentsOnlyToThePrivateBusinessBucket() {
        byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile(
                "rut",
                "rut.png",
                "application/octet-stream",
                pngSignature
        );

        fileService.uploadDocument(UUID.randomUUID(), ApplicationDocumentType.RUT, file);

        verify(storageService).uploadToBucket(
                eq("bussniess"),
                any(String.class),
                eq(pngSignature),
                eq("image/png"),
                eq(false)
        );
    }
}
