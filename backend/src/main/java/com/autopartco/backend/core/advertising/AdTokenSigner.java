package com.autopartco.backend.core.advertising;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Firma y verifica {@code adImpressionToken} (§2): HMAC-SHA256 sobre {@code campaignId|productId|placement|day|exp},
 * válido {@value #TTL_MINUTES} minutos. Forma: {@code base64url(payload) + "." + base64url(mac)}, sin relleno. La
 * verificación compara en tiempo constante ({@link MessageDigest#isEqual}) y nunca lanza: un token falso, alterado o
 * vencido da vacío.
 * <p>
 * Clave: se deriva (HMAC con una etiqueta fija) de {@code app.ads.token-secret}; si falta, de
 * {@code wompi.events-secret} y luego de {@code supabase.service-key}, igual que la sal de tráfico toma un valor
 * existente por defecto. Si no hay ninguno, se usa una clave aleatoria por proceso (los tokens dejan de valer al
 * reiniciar) y se avisa en el log.
 */
@Slf4j
@Component
public class AdTokenSigner {
    public static final long TTL_MINUTES = 30;
    static final int MAX_TOKEN_LENGTH = 400;
    /** Marca de {@code productId} ausente (anuncio de almacén, {@link AdPlacement#MAP_FEATURED}). */
    private static final String NO_PRODUCT = "-";
    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_LABEL = "autopartco-ad-token-v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Contenido verificado de un token. */
    public record Claims(UUID campaignId, UUID productId, AdPlacement placement, LocalDate day, Instant expiresAt) {
    }

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public AdTokenSigner(
            @Value("${app.ads.token-secret:}") String tokenSecret,
            @Value("${wompi.events-secret:}") String wompiEventsSecret,
            @Value("${supabase.service-key:}") String supabaseServiceKey
    ) {
        this(deriveKey(firstNonBlank(tokenSecret, wompiEventsSecret, supabaseServiceKey)), Clock.systemUTC());
    }

    AdTokenSigner(byte[] key, Clock clock) {
        this.key = key.clone();
        this.clock = clock;
    }

    /**
     * Token para mostrar {@code productId} de {@code campaignId} en {@code placement} hoy; vence en 30 minutos.
     * {@code productId} es nulo solo con {@link AdPlacement#MAP_FEATURED}.
     */
    public String sign(UUID campaignId, UUID productId, AdPlacement placement) {
        Instant now = clock.instant();
        return sign(campaignId, productId, placement, AdText.today(clock),
                now.plus(Duration.ofMinutes(TTL_MINUTES)));
    }

    String sign(UUID campaignId, UUID productId, AdPlacement placement, LocalDate day, Instant expiresAt) {
        String payload = campaignId + "|" + (productId == null ? NO_PRODUCT : productId) + "|" + placement.name() + "|" + day + "|"
                + expiresAt.getEpochSecond();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(mac(payloadBytes));
    }

    /** Contenido del token si la firma es válida y no venció; si no, vacío. */
    public Optional<Claims> verify(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            byte[] payloadBytes = DECODER.decode(token.substring(0, dot));
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(mac(payloadBytes), signature)) {
                return Optional.empty();
            }
            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5) {
                return Optional.empty();
            }
            Optional<AdPlacement> placement = AdPlacement.parse(parts[2]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            Instant now = clock.instant();
            if (placement.isEmpty() || !expiresAt.isAfter(now)
                    || expiresAt.isAfter(now.plus(Duration.ofMinutes(TTL_MINUTES + 1)))) {
                return Optional.empty();
            }
            UUID productId = NO_PRODUCT.equals(parts[1]) ? null : UUID.fromString(parts[1]);
            if ((productId == null) != (placement.get() == AdPlacement.MAP_FEATURED)) {
                return Optional.empty();
            }
            return Optional.of(new Claims(UUID.fromString(parts[0]), productId, placement.get(),
                    LocalDate.parse(parts[3]), expiresAt));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    static byte[] deriveKey(String material) {
        if (material == null) {
            log.warn("No ad token secret configured (app.ads.token-secret); using a random per-process key");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(material.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(KEY_LABEL.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
