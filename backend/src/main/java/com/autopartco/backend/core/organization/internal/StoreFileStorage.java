package com.autopartco.backend.core.organization.internal;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.shared.storage.SupabaseStorageProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class StoreFileStorage {
    private static final long MAX_DOCUMENT_SIZE = 10L * 1024 * 1024;
    private static final long MAX_BRAND_ASSET_SIZE = 5L * 1024 * 1024;

    private final SupabaseStorageService storageService;
    private final SupabaseStorageProperties storageProperties;

    public StoredFile uploadDocument(UUID applicationId, ApplicationDocumentType type, MultipartFile file) {
        byte[] content = read(file, MAX_DOCUMENT_SIZE);
        String contentType = detectContentType(content, false);
        String path = "applications/%s/documents/%s/%s.%s".formatted(
                applicationId,
                type.name().toLowerCase(Locale.ROOT),
                UUID.randomUUID(),
                extension(contentType)
        );
        storageService.uploadToBucket(storageProperties.businessBucket(), path, content, contentType, false);
        return new StoredFile(path, cleanFilename(file.getOriginalFilename()), contentType, content.length);
    }

    public StoredFile uploadBrandAsset(UUID storeId, String assetType, MultipartFile file) {
        byte[] content = read(file, MAX_BRAND_ASSET_SIZE);
        String contentType = detectContentType(content, true);
        String path = "stores/%s/brand/%s/%s.%s".formatted(
                storeId,
                assetType,
                UUID.randomUUID(),
                extension(contentType)
        );
        storageService.uploadToBucket(storageProperties.businessBucket(), path, content, contentType, false);
        return new StoredFile(path, cleanFilename(file.getOriginalFilename()), contentType, content.length);
    }

    public void delete(String path) {
        storageService.deleteFromBucket(storageProperties.businessBucket(), path);
    }

    public SupabaseStorageService.StoredObject download(String path) {
        return storageService.downloadFromBucket(storageProperties.businessBucket(), path);
    }

    private byte[] read(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "Debes seleccionar un archivo.");
        }
        if (file.getSize() > maxSize) {
            throw new BadRequestException("FILE_TOO_LARGE", "El archivo supera el tamaño máximo permitido.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("FILE_READ_FAILED", "No se pudo leer el archivo enviado.");
        }
    }

    private String detectContentType(byte[] content, boolean imageOnly) {
        if (content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-') {
            if (!imageOnly) {
                return "application/pdf";
            }
        }
        if (isPng(content)) {
            return "image/png";
        }
        if (isJpeg(content)) {
            return "image/jpeg";
        }
        if (imageOnly && isWebp(content)) {
            return "image/webp";
        }
        throw new BadRequestException(
                "UNSUPPORTED_FILE_TYPE",
                imageOnly
                        ? "Solo se permiten imágenes PNG, JPEG o WebP."
                        : "Solo se permiten documentos PDF o imágenes PNG/JPEG."
        );
    }

    private boolean isPng(byte[] content) {
        return content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4e
                && content[3] == 0x47
                && content[4] == 0x0d
                && content[5] == 0x0a
                && content[6] == 0x1a
                && content[7] == 0x0a;
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalStateException("Validated content type has no file extension.");
        };
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "archivo";
        }
        String cleaned = filename.replaceAll("[\\\\/\\r\\n\\p{Cntrl}]", "_").trim();
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    public record StoredFile(String path, String originalFilename, String contentType, long sizeBytes) {
    }
}
