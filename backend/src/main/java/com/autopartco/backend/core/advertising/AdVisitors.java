package com.autopartco.backend.core.advertising;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final String salt;

    public AdVisitors(@Value("${app.traffic.salt:${app.public-base-url:autopartco}}") String salt) {
        this.salt = salt;
    }

    public String ofAccount(UUID accountId) {
        return sha256(salt + "|ads|u:" + accountId);
    }

    /**
     * Nulo si el id anónimo está vacío o es demasiado largo. Un UUID se normaliza a minúsculas, la forma en que lo
     * devuelve {@code order_attributions.anonymous_id}, para que los clics y el pedido den el mismo hash.
     */
    public String ofAnonymous(String anonymousId) {
        if (anonymousId == null || anonymousId.isBlank() || anonymousId.length() > MAX_ANONYMOUS_ID) {
            return null;
        }
        String value = anonymousId.strip();
        if (UUID_FORMAT.matcher(value).matches()) {
            value = value.toLowerCase(Locale.ROOT);
        }
        return sha256(salt + "|ads|a:" + value);
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
