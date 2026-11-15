package com.autopartco.backend.shared.web;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente para límites por IP, bloqueos de login y registros.
 * <p>
 * {@code getRemoteAddr()} es el par que abrió la conexión: en producción nginx (127.0.0.1) reenvía {@code X-Forwarded-For}
 * y Tomcat ({@code forward-headers-strategy: native}) lo recorre de derecha a izquierda saltando proxies internos, así que
 * queda la IP que vio nginx, no la que escribió el cliente. {@code CF-Connecting-IP} solo se acepta si ese par está en
 * {@link #trustProxies rangos confiables} (por defecto los de Cloudflare): si la API se expone sin Cloudflare, cualquier
 * cliente podría poner una IP distinta en cada petición y saltarse los límites.
 * <p>
 * El valor siempre es una IP sin corchetes ni puerto: tras un proxy, {@code getRemoteAddr()} puede devolver IPv6 como
 * {@code [2800:e2::1]} y PostgreSQL rechaza ese texto en columnas {@code inet} (500 en login y sesiones). Si no hay una
 * IP válida se usa {@link #UNKNOWN}, porque {@code login_attempts} y {@code account_sessions} la exigen.
 */
public final class ClientIp {
    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";
    static final String UNKNOWN = "0.0.0.0";
    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    /** {@code [ipv6]} o {@code [ipv6]:puerto}. */
    private static final Pattern BRACKETED = Pattern.compile("^\\[([^\\]]+)\\](?::\\d{1,5})?$");

    /** https://www.cloudflare.com/ips-v4 y https://www.cloudflare.com/ips-v6 */
    public static final List<String> CLOUDFLARE_RANGES = List.of(
            "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22", "141.101.64.0/18",
            "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20", "197.234.240.0/22", "198.41.128.0/17",
            "162.158.0.0/15", "104.16.0.0/13", "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
            "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32", "2405:8100::/32",
            "2a06:98c0::/29", "2c0f:f248::/32");

    private static volatile List<IpAddressMatcher> trusted = compile(CLOUDFLARE_RANGES);

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (remote != null && isTrusted(remote)) {
            String cloudflare = normalize(request.getHeader(CLOUDFLARE_HEADER));
            if (cloudflare != null) {
                return cloudflare;
            }
        }
        return remote != null ? remote : UNKNOWN;
    }

    /** Sustituye los rangos (CIDR o IP) desde los que se acepta {@code CF-Connecting-IP}. Lista vacía: nunca. */
    public static void trustProxies(List<String> ranges) {
        trusted = compile(ranges);
    }

    static boolean isTrusted(String ip) {
        // Solo literales IP: IpAddressMatcher resolvería por DNS un texto como "dead" o "beef".
        if (!ip.contains(":") && !IPV4.matcher(ip).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : trusted) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return false;
    }

    private static List<IpAddressMatcher> compile(List<String> ranges) {
        return ranges == null ? List.of()
                : ranges.stream().map(String::strip).filter(r -> !r.isEmpty()).map(IpAddressMatcher::new).toList();
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
