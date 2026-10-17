package com.autopartco.backend.shared.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * URL de una imagen de Storage en un tamaño dado. El proveedor de transformación se elige con {@code image.provider}:
 * {@code none} (URL pública de Supabase, por defecto), {@code wsrv}, {@code cloudinary} (modo fetch) u
 * {@code openinary} (autoalojado, requiere {@code openinary.base-url}). Un proveedor mal configurado (sin cloud name
 * o sin base-url) o desconocido degrada a la URL pública de Supabase en vez de romper las respuestas.
 */
@Component
public class ImageUrls {
    private final SupabaseStorageService storage;
    private final String provider;
    private final String cloudinaryCloudName;
    private final String openinaryBaseUrl;

    public ImageUrls(
            SupabaseStorageService storage,
            @Value("${image.provider:none}") String provider,
            @Value("${image.cloudinary.cloud-name:}") String cloudinaryCloudName,
            @Value("${openinary.base-url:}") String openinaryBaseUrl
    ) {
        this.storage = storage;
        this.provider = provider == null ? "none" : provider.strip().toLowerCase(Locale.ROOT);
        this.cloudinaryCloudName = cloudinaryCloudName == null ? "" : cloudinaryCloudName.strip();
        String base = openinaryBaseUrl == null ? "" : openinaryBaseUrl.strip();
        this.openinaryBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** URL original (sin transformar). */
    public String original(String storagePath) {
        return storage.publicUrl(storagePath);
    }

    public String variant(String storagePath, ImageVariant variant) {
        String publicUrl = storage.publicUrl(storagePath);
        int w = variant.width();
        int h = variant.height();
        return switch (provider) {
            case "wsrv" -> "https://wsrv.nl/?url=" + URLEncoder.encode(publicUrl, StandardCharsets.UTF_8)
                    + "&w=" + w + "&h=" + h + "&fit=cover&output=webp&q=80";
            case "cloudinary" -> cloudinaryCloudName.isEmpty() ? publicUrl
                    : "https://res.cloudinary.com/" + cloudinaryCloudName + "/image/fetch/w_" + w + ",h_" + h
                            + ",c_fill,f_auto,q_auto/" + publicUrl;
            case "openinary" -> openinaryBaseUrl.isEmpty() ? publicUrl
                    : openinaryBaseUrl + "/t/w_" + w + ",h_" + h + ",c_fill,f_webp/"
                            + (storagePath.startsWith("/") ? storagePath.substring(1) : storagePath);
            default -> publicUrl;
        };
    }
}
