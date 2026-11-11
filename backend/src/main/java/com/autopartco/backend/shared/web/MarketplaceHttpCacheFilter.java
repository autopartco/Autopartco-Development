package com.autopartco.backend.shared.web;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caché HTTP de los GET públicos del marketplace (§4 del plan v2.0.0): {@code ETag} débil del cuerpo y 304 ante
 * {@code If-None-Match}, con {@code Cache-Control} según la ruta y la sesión.
 * <ul>
 *   <li>{@link Policy#PUBLIC} (sugerencias, perfil y sucursales de tienda, detalle de producto, opciones y categorías):
 *   sin sesión {@value #PUBLIC_CACHE} y {@code Vary: Authorization} (un intermediario nunca sirve la versión anónima a
 *   quien trae token).</li>
 *   <li>{@link Policy#PRIVATE} ({@code /search}): siempre {@value #PRIVATE_CACHE}. El resultado depende del visitante
 *   (tope de frecuencia de anuncios, cookie {@code ap_area}), así que ningún intermediario debe compartirlo; el
 *   navegador revalida con el ETag y recibe 304 si no cambió.</li>
 *   <li>Con sesión ({@code Authorization} o cookie de sesión): {@value #PRIVATE_CACHE} y
 *   {@code Vary: Authorization, Cookie}. Nunca {@code public}.</li>
 * </ul>
 * Una respuesta que el controlador marcó {@code no-store} (p. ej. {@code /search} con anuncios patrocinados, que llevan
 * tokens de impresión) sale intacta: sin ETag ni 304. Solo respuestas 200; los errores no se tocan. {@code /home} y
 * {@code /ads} no pasan por aquí ({@code no-store} por los tokens).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class MarketplaceHttpCacheFilter extends OncePerRequestFilter {
    static final String PUBLIC_CACHE = "public, max-age=30, stale-while-revalidate=120";
    static final String PRIVATE_CACHE = "private, no-cache";
    /** Cookie del refresh token ({@code core.identity.web.RefreshCookie#NAME}); cuenta como sesión. */
    static final String SESSION_COOKIE = "autopartco_refresh";

    enum Policy { PUBLIC, PRIVATE }

    private record Route(Pattern pattern, Policy policy) {
    }

    private static final String M = Pattern.quote(ApiPaths.MARKETPLACE);
    private static final List<Route> ROUTES = List.of(
            new Route(Pattern.compile(M + "/search"), Policy.PRIVATE),
            new Route(Pattern.compile(M + "/search/suggest"), Policy.PUBLIC),
            new Route(Pattern.compile(M + "/stores/[^/]+"), Policy.PUBLIC),
            new Route(Pattern.compile(M + "/stores/[^/]+/branches"), Policy.PUBLIC),
            new Route(Pattern.compile(M + "/products/[^/]+"), Policy.PUBLIC),
            new Route(Pattern.compile(M + "/product-options(/[^/]+)?"), Policy.PUBLIC));

    private final boolean enabled;

    public MarketplaceHttpCacheFilter(@Value("${app.http-cache.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    static Policy policyFor(String path) {
        for (Route route : ROUTES) {
            if (route.pattern().matcher(path).matches()) {
                return route.policy();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"GET".equals(request.getMethod()) || policyFor(path(request)) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policyFor(path(request));
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        String controllerCache = wrapper.getHeader(HttpHeaders.CACHE_CONTROL);
        if (wrapper.getStatus() != HttpServletResponse.SC_OK
                || (controllerCache != null && controllerCache.contains("no-store"))) {
            wrapper.copyBodyToResponse();
            return;
        }
        boolean session = hasSession(request);
        if (session || policy == Policy.PRIVATE) {
            wrapper.setHeader(HttpHeaders.CACHE_CONTROL, PRIVATE_CACHE);
            wrapper.addHeader(HttpHeaders.VARY, "Authorization, Cookie");
        } else {
            wrapper.setHeader(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE);
            wrapper.addHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
        }
        String etag = "W/\"" + DigestUtils.md5DigestAsHex(wrapper.getContentAsByteArray()) + "\"";
        wrapper.setHeader(HttpHeaders.ETAG, etag);
        if (matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        wrapper.copyBodyToResponse();
    }

    static boolean hasSession(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Comparación débil (RFC 9110 §13.1.2): ignora el prefijo {@code W/}; {@code *} coincide con cualquiera. */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String opaque = weakless(etag);
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.strip();
            if (trimmed.equals("*") || weakless(trimmed).equals(opaque)) {
                return true;
            }
        }
        return false;
    }

    private static String weakless(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length())
                : uri;
    }
}
