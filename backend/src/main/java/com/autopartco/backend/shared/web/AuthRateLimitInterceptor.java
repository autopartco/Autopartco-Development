package com.autopartco.backend.shared.web;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite por IP y ruta en los flujos públicos de {@code /auth} que prueban credenciales o códigos, o envían correos:
 * login (contraseña, 2FA, social), verificación de correo y reenvíos, desbloqueo y recuperación de contraseña; además
 * {@code POST /account/settings/two-factor/disable} (contraseña). Por
 * defecto 10 por minuto ({@code app.auth-rate-limit.per-minute}); con Redis el límite es común a todas las
 * instancias. Exceso: 429 {@value #ERROR_CODE} (vía {@code GlobalExceptionHandler}). Complementa el bloqueo por
 * cuenta de {@code LoginAttemptService}.
 */
@Configuration(proxyBeanMethods = false)
public class AuthRateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    static final String ERROR_CODE = "AUTH_RATE_LIMITED";
    static final String AUTH = ApiPaths.BASE + "/auth";
    /** Desactivar la 2FA con contraseña: prueba credenciales aunque esté fuera de {@code /auth}. */
    static final String TWO_FACTOR_DISABLE = ApiPaths.BASE + "/account/settings/two-factor/disable";

    /** Ruta → grupo (clave del contador). Los dos pasos del login comparten grupo por método. */
    static final Map<String, String> POST_ROUTES = Map.of(
            AUTH + "/login", "login",
            AUTH + "/login/two-factor", "login-2fa",
            AUTH + "/login/two-factor/resend", "login-2fa-resend",
            AUTH + "/verify-email/code", "verify",
            AUTH + "/verify-email/resend", "verify-resend",
            AUTH + "/unlock/request", "unlock",
            AUTH + "/password-recovery/request", "recovery",
            TWO_FACTOR_DISABLE, "2fa-disable");
    static final String SOCIAL_PREFIX = AUTH + "/social/";
    static final String VERIFY_LINK = AUTH + "/verify-email";

    private final SlidingWindowRateLimiter limiter;

    public AuthRateLimitInterceptor(RateLimiters rateLimiters,
            @Value("${app.auth-rate-limit.per-minute:10}") int perMinute) {
        this.limiter = rateLimiters.create("auth", ERROR_CODE, perMinute, Duration.ofMinutes(1));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(AUTH + "/**", TWO_FACTOR_DISABLE);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String group = group(request.getMethod(), request.getRequestURI().substring(request.getContextPath().length()));
        if (group != null) {
            limiter.acquire(group + ":" + request.getRemoteAddr());
        }
        return true;
    }

    static String group(String method, String path) {
        if ("POST".equals(method)) {
            String group = POST_ROUTES.get(path);
            if (group != null) {
                return group;
            }
            return path.startsWith(SOCIAL_PREFIX) ? "social" : null;
        }
        if ("GET".equals(method) && VERIFY_LINK.equals(path)) {
            return "verify-link";
        }
        return null;
    }
}
