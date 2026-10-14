package com.autopartco.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;


/**
 * Equivalencia de {@link TokenHashing} con las implementaciones que reemplaza. Las copias de abajo reproducen
 * literalmente el código anterior a la consolidación (fase 7) de {@code StaffInvitations#hash},
 * {@code StoreApplications#hash}, {@code RefreshTokenHash#of} y {@code WompiHashing#sha256} (envoltorios ya retirados; los llamadores usan {@link TokenHashing} directamente).
 */
class TokenHashingTest {

    private static final List<String> INPUTS = List.of(
            "",
            "abc",
            "The quick brown fox jumps over the lazy dog",
            "ñandú-ÁÉÍÓÚ-€-日本-🚗",
            "Kx3_uQ9-aZ0bYc1dEf2gHi3jKl4mNo5pQr6sTu7vWxY",
            "REF-123" + 15_000_000L + "COP" + "integrity-secret"
    );

    @Test
    void matchesKnownSha256Vectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", TokenHashing.sha256Hex(""));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHashing.sha256Hex("abc"));
        assertEquals(
                "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                TokenHashing.sha256Hex("The quick brown fox jumps over the lazy dog")
        );
        // Vector heredado de RefreshTokenHashTest (account_sessions.refresh_token_hash).
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", TokenHashing.sha256Hex("hello"));
    }

    @Test
    void matchesEveryReplacedImplementation() throws Exception {
        for (String input : INPUTS) {
            String expected = TokenHashing.sha256Hex(input);
            assertEquals(expected, oldStaffInvitationsHash(input), input);
            assertEquals(expected, oldStoreApplicationsHash(input), input);
            assertEquals(expected, oldRefreshTokenHash(input), input);
            assertEquals(expected, oldWompiSha256(input), input);
            assertTrue(expected.matches("[0-9a-f]{64}"), input);
        }
    }

    @Test
    void newTokenIsUrlSafeBase64Of32BytesWithoutPadding() {
        String token = TokenHashing.newToken();

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
        assertNotEquals(token, TokenHashing.newToken());
    }

    // --- Copias literales de las implementaciones anteriores ---

    private static String oldStaffInvitationsHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static String oldStoreApplicationsHash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String oldRefreshTokenHash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static String oldWompiSha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
