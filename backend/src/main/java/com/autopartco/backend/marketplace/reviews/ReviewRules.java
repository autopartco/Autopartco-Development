package com.autopartco.backend.marketplace.reviews;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

/** Reglas comunes de las reseñas de almacén y de producto: comentario, categorías y ventana de edición. */
final class ReviewRules {
    /** Se edita hasta 30 días después de crearla. Borrar se puede siempre. */
    static final Duration EDIT_WINDOW = Duration.ofDays(30);
    static final int MAX_COMMENT = 1000;

    /**
     * Enlaces: esquema ({@code http://}), {@code www.} o un dominio con un TLD común seguido de fin de palabra o
     * {@code /}. No pretende ser exhaustivo; frena el spam con enlaces sin rechazar «4.5» o «S.A.».
     */
    static final Pattern LINK = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://|\\bwww\\.|\\b[a-z0-9][a-z0-9-]*\\.(com|co|net|org|info|biz|io|me|ly|app|"
                    + "xyz|online|store|shop|site|top|link|click|gl|es|us|tk|ru|cn)(/|\\b))");

    private ReviewRules() {
    }

    /** Recorta espacios; vacío → {@code null}. 400 {@code REVIEW_LINKS_NOT_ALLOWED} si contiene un enlace. */
    static String comment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_COMMENT) {
            throw new BadRequestException("VALIDATION_ERROR", "El comentario no puede superar 1000 caracteres.");
        }
        if (LINK.matcher(trimmed).find()) {
            throw new BadRequestException("REVIEW_LINKS_NOT_ALLOWED", "El comentario no puede incluir enlaces.");
        }
        return trimmed;
    }

    /**
     * Categorías del tipo, con códigos en mayúsculas y nota 1–5. {@code null} = ninguna. Otra categoría u otra nota:
     * 400 {@code REVIEW_ASPECT_INVALID}.
     */
    static Map<String, Integer> aspects(ReviewKind kind, Map<String, Integer> raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            String code = entry.getKey() == null ? "" : entry.getKey().strip().toUpperCase(Locale.ROOT);
            Integer rating = entry.getValue();
            if (!kind.aspects().contains(code) || rating == null || rating < 1 || rating > 5) {
                throw new BadRequestException("REVIEW_ASPECT_INVALID",
                        "Las categorías válidas son " + String.join(", ", kind.aspects())
                                + " y se califican de 1 a 5.");
            }
            result.put(code, rating);
        }
        // Orden estable (el del enum) para guardar y responder.
        Map<String, Integer> ordered = new LinkedHashMap<>();
        kind.aspects().forEach(code -> {
            if (result.containsKey(code)) {
                ordered.put(code, result.get(code));
            }
        });
        return ordered;
    }

    static OffsetDateTime editableUntil(OffsetDateTime createdAt) {
        return createdAt == null ? null : createdAt.plus(EDIT_WINDOW);
    }

    /**
     * La reseña existe, es del objeto calificado y del autor; si no, 404 {@code REVIEW_NOT_FOUND} (no revela si es de
     * otra persona).
     */
    static ReviewQueries.OwnedReview requireOwn(Optional<ReviewQueries.OwnedReview> found, UUID scopeId,
            UUID personId) {
        return found
                .filter(review -> review.scopeId().equals(scopeId) && review.personId().equals(personId))
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));
    }

    /** 409 {@code REVIEW_EDIT_WINDOW_CLOSED} pasados 30 días desde la creación. */
    static void requireEditable(ReviewQueries.OwnedReview review, OffsetDateTime now) {
        OffsetDateTime until = editableUntil(review.createdAt());
        if (until == null || now.isAfter(until)) {
            throw new ConflictException("REVIEW_EDIT_WINDOW_CLOSED",
                    "Las reseñas se pueden editar hasta 30 días después de publicarlas.");
        }
    }
}
