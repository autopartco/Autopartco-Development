package com.autopartco.backend.core.catalog.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.PayloadTooLargeException;

class ProductImageValidatorTest {
    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN9sAAAAASUVORK5CYII=";

    private final ProductImageValidator validator = new ProductImageValidator();

    @Test
    void acceptsValidPngAndKeepsItsOriginalFormat() {
        var result = validator.validate(new MockMultipartFile(
                "file",
                "part.png",
                "image/png",
                Base64.getDecoder().decode(ONE_PIXEL_PNG)
        ));

        assertEquals("image/png", result.contentType());
        assertEquals("png", result.extension());
    }

    @Test
    void rejectsPdfWithAnImageMimeType() {
        assertThrows(BadRequestException.class, () -> validator.validate(new MockMultipartFile(
                "file",
                "part.png",
                "image/png",
                "%PDF-1.7".getBytes()
        )));
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchTheFileSignature() {
        assertThrows(BadRequestException.class, () -> validator.validate(new MockMultipartFile(
                "file",
                "part.jpg",
                "image/jpeg",
                Base64.getDecoder().decode(ONE_PIXEL_PNG)
        )));
    }

    @Test
    void rejectsImagesLargerThanFiveMegabytes() {
        assertThrows(PayloadTooLargeException.class, () -> validator.validate(new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[ProductImageValidator.MAX_FILE_SIZE_BYTES + 1]
        )));
    }

    @Test
    void acceptsWebpWithAValidRiffAndImageChunk() {
        byte[] webp = new byte[30];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        ByteBuffer.wrap(webp, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(webp.length - 8);
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        webp[12] = 'V';
        webp[13] = 'P';
        webp[14] = '8';
        webp[15] = 'X';
        ByteBuffer.wrap(webp, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(10);

        var result = validator.validate(new MockMultipartFile(
                "file", "part.webp", "image/webp", webp));

        assertEquals("image/webp", result.contentType());
        assertEquals("webp", result.extension());
    }
}
