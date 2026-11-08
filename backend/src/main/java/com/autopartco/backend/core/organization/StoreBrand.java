package com.autopartco.backend.core.organization;

import java.util.Set;
import java.util.UUID;

import com.autopartco.backend.shared.web.ApiPaths;

/** Tipos de recurso de marca y su URL pública. */
public final class StoreBrand {
    public static final String LOGO = "logo";
    public static final String BANNER = "banner";
    public static final Set<String> TYPES = Set.of(LOGO, BANNER);

    private StoreBrand() {
    }

    /**
     * URL pública (sirve solo si el almacén está publicado) o {@code null} si no hay archivo. {@code v} cambia con cada
     * carga para que el caché del navegador no muestre la imagen anterior.
     */
    public static String url(UUID storeId, String assetType, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String version = dot > 0 ? file.substring(0, dot) : file;
        return ApiPaths.MARKETPLACE + "/stores/" + storeId + "/brand/" + assetType + "?v=" + version;
    }
}
