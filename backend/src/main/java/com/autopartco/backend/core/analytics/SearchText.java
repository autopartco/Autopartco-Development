package com.autopartco.backend.core.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización del texto de búsqueda y seudónimo diario de la sesión (sin datos personales). */
public final class SearchText {
    public static final int MAX_LENGTH = 200;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    /** Texto recortado con espacios colapsados (máx. 200); {@code null} si queda vacío. */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String collapsed = SPACES.matcher(text.strip()).replaceAll(" ");
        return collapsed.isEmpty() ? null : truncate(collapsed);
    }

    /** Minúsculas, sin tildes, espacios colapsados y recortado a 200; {@code null} si queda vacío. */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String withoutMarks = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String collapsed = SPACES.matcher(withoutMarks.toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
        return collapsed.isEmpty() ? null : truncate(collapsed);
    }

    /**
     * SHA-256 en hexadecimal de {@code sessionId + fecha}: agrupa las búsquedas de una sesión dentro del día sin
     * guardar el identificador. {@code null} si no hay sesión.
     */
    public static String sessionHash(String sessionId, LocalDate day) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((sessionId.strip() + day).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 no disponible", unavailable);
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH).strip();
    }
}
