package com.autopartco.backend.shared.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente para límites por IP, bloqueos de login y registros. En producción la API pasa por Cloudflare y
 * Render: el cliente puede enviar su propio {@code X-Forwarded-For} y los proxies le añaden la IP real al final, así que
 * {@code getRemoteAddr()} con {@code forward-headers-strategy: framework} (que toma la de más a la izquierda) es
 * falsificable. {@code CF-Connecting-IP} lo fija Cloudflare con la IP que abrió la conexión y sobrescribe cualquier valor
 * del cliente. Sin ese encabezado (desarrollo, pruebas, nginx del VPS) se usa {@code getRemoteAddr()}.
 * <p>
 * El valor siempre es una IP sin corchetes ni puerto: tras un proxy, {@code getRemoteAddr()} puede devolver IPv6 como
 * {@code [2800:e2::1]} y PostgreSQL rechaza ese texto en columnas {@code inet} (500 en login y sesiones). Si no hay una
 * IP válida se usa {@link #UNKNOWN}, porque {@code login_attempts} y {@code account_sessions} la exigen.
 */
public final class ClientIp {
    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";
    static final String UNKNOWN = "0.0.0.0";
    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    /** {@code [ipv6]} o {@code [ipv6]:puerto}. */
    private static final Pattern BRACKETED = Pattern.compile("^\\[([^\\]]+)\\](?::\\d{1,5})?$");

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String cloudflare = normalize(request.getHeader(CLOUDFLARE_HEADER));
        if (cloudflare != null) {
            return cloudflare;
        }
        String remote = normalize(request.getRemoteAddr());
        return remote != null ? remote : UNKNOWN;
    }

    /** IP sin espacios, corchetes ni puerto; {@code null} si no parece una IP. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        Matcher bracketed = BRACKETED.matcher(value);
        if (bracketed.matches()) {
            value = bracketed.group(1);
        }
        return IP.matcher(value).matches() ? value : null;
    }
}
