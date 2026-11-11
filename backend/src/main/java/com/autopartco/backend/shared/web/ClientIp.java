package com.autopartco.backend.shared.web;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente para límites por IP, bloqueos de login y registros. En producción la API pasa por Cloudflare y
 * Render: el cliente puede enviar su propio {@code X-Forwarded-For} y los proxies le añaden la IP real al final, así que
 * {@code getRemoteAddr()} con {@code forward-headers-strategy: framework} (que toma la de más a la izquierda) es
 * falsificable. {@code CF-Connecting-IP} lo fija Cloudflare con la IP que abrió la conexión y sobrescribe cualquier valor
 * del cliente. Sin ese encabezado (desarrollo, pruebas) se usa {@code getRemoteAddr()}.
 */
public final class ClientIp {
    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";
    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String cloudflare = request.getHeader(CLOUDFLARE_HEADER);
        if (cloudflare != null) {
            String value = cloudflare.strip();
            if (IP.matcher(value).matches()) {
                return value;
            }
        }
        return request.getRemoteAddr();
    }
}
