package com.autopartco.backend.product.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.PayloadTooLargeException;

@Component
public class ProductImageValidator {
    public static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 40_000_000L;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidImage();
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new PayloadTooLargeException(
                    "PRODUCT_IMAGE_TOO_LARGE",
                    "El archivo supera el tamaño máximo permitido de 5 MB."
            );
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw invalidImage();
        }
        if (content.length == 0) {
            throw invalidImage();
        }
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new PayloadTooLargeException(
                    "PRODUCT_IMAGE_TOO_LARGE",
                    "El archivo supera el tamaño máximo permitido de 5 MB."
            );
        }

        String detectedType = detectContentType(content);
        String declaredType = file.getContentType();
        if (detectedType == null || declaredType == null
                || !detectedType.equals(declaredType.toLowerCase(Locale.ROOT))) {
            throw invalidImage();
        }
        validateImageContent(content, detectedType);

        String extension = switch (detectedType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalidImage();
        };
        return new ValidatedImage(content, detectedType, extension);
    }

    private String detectContentType(byte[] content) {
        if (content.length >= 8
                && unsigned(content[0]) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4e
                && content[3] == 0x47
                && content[4] == 0x0d
                && content[5] == 0x0a
                && content[6] == 0x1a
                && content[7] == 0x0a) {
            return "image/png";
        }
        if (content.length >= 3
                && unsigned(content[0]) == 0xff
                && unsigned(content[1]) == 0xd8
                && unsigned(content[2]) == 0xff) {
            return "image/jpeg";
        }
        if (isWebpContainer(content)) {
            return "image/webp";
        }
        return null;
    }

    private void validateImageContent(byte[] content, String contentType) {
        if ("image/webp".equals(contentType)) {
            validateWebp(content);
            return;
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private boolean isWebpContainer(byte[] content) {
        return content.length >= 20
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P'
                && unsignedInt32(content, 4) + 8L == content.length;
    }

    private void validateWebp(byte[] content) {
        String chunkType = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        long chunkLength = unsignedInt32(content, 16);
        if (chunkLength > content.length - 20L) {
            throw invalidImage();
        }

        int width;
        int height;
        if ("VP8X".equals(chunkType) && chunkLength >= 10) {
            width = 1 + unsignedInt24(content, 24);
            height = 1 + unsignedInt24(content, 27);
        } else if ("VP8L".equals(chunkType) && chunkLength >= 5
                && unsigned(content[20]) == 0x2f) {
            width = 1 + unsigned(content[21]) + ((unsigned(content[22]) & 0x3f) << 8);
            height = 1 + ((unsigned(content[22]) & 0xc0) >> 6)
                    + (unsigned(content[23]) << 2) + ((unsigned(content[24]) & 0x0f) << 10);
        } else if ("VP8 ".equals(chunkType) && chunkLength >= 10
                && unsigned(content[23]) == 0x9d
                && unsigned(content[24]) == 0x01
                && unsigned(content[25]) == 0x2a) {
            width = ((unsigned(content[27]) & 0x3f) << 8) | unsigned(content[26]);
            height = ((unsigned(content[29]) & 0x3f) << 8) | unsigned(content[28]);
        } else {
            throw invalidImage();
        }
        validateDimensions(width, height);
    }

    private int unsignedInt24(byte[] content, int offset) {
        return unsigned(content[offset])
                | (unsigned(content[offset + 1]) << 8)
                | (unsigned(content[offset + 2]) << 16);
    }

    private long unsignedInt32(byte[] content, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(content, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt());
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXEL_COUNT) {
            throw invalidImage();
        }
    }

    private BadRequestException invalidImage() {
        return new BadRequestException(
                "INVALID_PRODUCT_IMAGE",
                "Formato de imagen no permitido. Usa una imagen JPEG, PNG o WebP válida."
        );
    }

    public record ValidatedImage(byte[] content, String contentType, String extension) {
    }
}
