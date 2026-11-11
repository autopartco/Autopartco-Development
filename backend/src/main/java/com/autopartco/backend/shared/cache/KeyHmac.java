package com.autopartco.backend.shared.cache;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 con la sal de tráfico ({@code app.traffic.salt}) para la parte de una clave de Redis que identifica a una
 * persona (IP, visitante, cuenta): sin la sal no se puede recuperar el valor probando IPs o ids. Hex de 32 caracteres
 * (128 bits). Solo para claves; nunca se guarda en la base de datos.
 */
public final class KeyHmac {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int BYTES = 16;

    private final SecretKeySpec key;

    public KeyHmac(String salt) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("La sal de las claves no puede estar vacía.");
        }
        this.key = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, BYTES);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }
}
