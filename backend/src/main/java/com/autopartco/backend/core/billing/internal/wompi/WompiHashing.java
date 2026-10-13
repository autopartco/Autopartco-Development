package com.autopartco.backend.core.billing.internal.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 en hexadecimal (minúsculas) que usa Wompi para la firma de integridad del checkout y el checksum de
 * eventos.
 */
public final class WompiHashing {
    private WompiHashing() {
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
