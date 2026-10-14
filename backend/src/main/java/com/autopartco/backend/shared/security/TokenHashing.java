package com.autopartco.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos de un solo uso y su hash: se entrega el token en claro y se guarda solo
 * {@link #sha256Hex(String)} (SHA-256 sobre UTF-8, hexadecimal en minúsculas).
 */
public final class TokenHashing {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenHashing() {
    }

    /** SHA-256 de {@code value} codificado en UTF-8, en hexadecimal de 64 caracteres en minúsculas. */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    /** 32 bytes aleatorios en Base64 URL sin relleno (43 caracteres). */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
