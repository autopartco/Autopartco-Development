package com.autopartco.backend.core.identity.internal;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.identity.AccountConfigurations;

/**
 * Aviso «Nuevo inicio de sesión» ({@code security.loginAlerts}, activo por defecto). Se avisa si la cuenta ya tenía
 * sesiones (no en el primer login) y ninguna con el mismo user-agent normalizado (sin números de versión). Hay que
 * decidirlo antes de crear la sesión nueva.
 */
@Component
public class LoginAlerts {
    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a",
            Locale.forLanguageTag("es-CO"));

    private final AccountSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public LoginAlerts(AccountSessionRepository sessionRepository, ApplicationEventPublisher eventPublisher) {
        this(sessionRepository, eventPublisher, Clock.systemUTC());
    }

    LoginAlerts(AccountSessionRepository sessionRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** {@code true} si hay que avisar del login que está por abrir sesión con {@code userAgent}. */
    public boolean shouldAlert(Account account, String userAgent) {
        if (!AccountConfigurations.loginAlertsEnabled(account.getConfiguration())) {
            return false;
        }
        List<String> previous = sessionRepository.findDistinctUserAgentsByAccountId(account.getId());
        if (previous.isEmpty()) {
            return false;
        }
        String current = normalize(userAgent);
        return previous.stream().map(LoginAlerts::normalize).noneMatch(current::equals);
    }

    public void publish(Account account, String ipAddress, String userAgent) {
        eventPublisher.publishEvent(new LoginAlertRequested(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                ZonedDateTime.now(clock).withZoneSameInstant(BOGOTA).format(WHEN),
                describe(userAgent),
                maskIp(ipAddress)
        ));
    }

    /** Minúsculas, sin números (versiones) y con espacios simples: una actualización del navegador no avisa. */
    static String normalize(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.toLowerCase(Locale.ROOT).replaceAll("\\d+", "#").replaceAll("\\s+", " ").trim();
    }

    /** Navegador y sistema aproximados, p. ej. «Chrome en Windows». */
    static String describe(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        String browser;
        if (ua.contains("edg/")) browser = "Edge";
        else if (ua.contains("opr/") || ua.contains("opera")) browser = "Opera";
        else if (ua.contains("samsungbrowser")) browser = "Samsung Internet";
        else if (ua.contains("firefox/") || ua.contains("fxios")) browser = "Firefox";
        else if (ua.contains("chrome/") || ua.contains("crios")) browser = "Chrome";
        else if (ua.contains("safari/")) browser = "Safari";
        else browser = "Navegador desconocido";

        String system;
        if (ua.contains("android")) system = "Android";
        else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) system = "iOS";
        else if (ua.contains("windows")) system = "Windows";
        else if (ua.contains("mac os") || ua.contains("macintosh")) system = "macOS";
        else if (ua.contains("cros")) system = "ChromeOS";
        else if (ua.contains("linux")) system = "Linux";
        else system = "sistema desconocido";
        return browser + " en " + system;
    }

    /** IPv4 sin el último octeto ({@code 190.24.10.***}); IPv6 solo con los tres primeros grupos. */
    static String maskIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "desconocida";
        }
        String ip = ipAddress.trim();
        if (ip.contains(":")) {
            String[] groups = ip.split(":");
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < Math.min(3, groups.length); i++) {
                masked.append(groups[i]).append(':');
            }
            return masked.append("***").toString();
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? "***" : ip.substring(0, lastDot) + ".***";
    }
}
