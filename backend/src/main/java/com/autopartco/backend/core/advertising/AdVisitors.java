package com.autopartco.backend.core.advertising;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code ad_events.visitor_hash}: SHA-256 hexadecimal (64 caracteres) de la sal de tráfico y el identificador del
 * visitante. Con sesión se usa la cuenta ({@code u:}); sin sesión, el id anónimo del navegador ({@code a:}). A
 * diferencia de las visitas, no rota por día: la atribución de pedidos mira 7 días atrás. No se guarda nada que
 * identifique a la persona por sí solo (Ley 1581).
 */
@Component
public class AdVisitors {
    static final int MAX_ANONYMOUS_ID = 100;

    private final String salt;

    public AdVisitors(@Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt) {
        this.salt = salt;
    }

    public String ofAccount(UUID accountId) {
        return sha256(salt + "|ads|u:" + accountId);
    }

    /** Nulo si el id anónimo está vacío o es demasiado largo. */
    public String ofAnonymous(String anonymousId) {
        if (anonymousId == null || anonymousId.isBlank() || anonymousId.length() > MAX_ANONYMOUS_ID) {
            return null;
        }
        return sha256(salt + "|ads|a:" + anonymousId.strip());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
