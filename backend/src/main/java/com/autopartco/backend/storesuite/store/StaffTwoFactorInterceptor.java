package com.autopartco.backend.storesuite.store;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

/**
 * {@code security.requireStaffTwoFactor}: si el almacén lo exige, toda petición a {@code /api/v1/stores/{storeId}/**}
 * de un colaborador (incluido el dueño) sin 2FA activa responde 403 {@code STAFF_TWO_FACTOR_REQUIRED}. Exentas:
 * {@code /stores/{storeId}/settings} (se puede leer; el PATCH lo protege {@code SettingsService.patchStore}) y las rutas fuera de {@code /stores}
 * (p. ej. {@code /account/settings}). La bandera del almacén sale de la caché de {@link SettingsQueries}; solo si está
 * activa se consulta la cuenta (y la membresía, si la cuenta no tiene 2FA). Quien no es del almacén sigue de largo: el
 * caso de uso lo rechaza como siempre.
 */
@Component
@AllArgsConstructor
public class StaffTwoFactorInterceptor implements HandlerInterceptor {
    static final String EXEMPT_SUFFIX = "/settings";
    private static final Pattern STORE_PATH = Pattern.compile(
            "^" + Pattern.quote(ApiPaths.STORES) + "/([0-9a-fA-F-]{36})(/.*)?$");

    private final SettingsQueries settingsQueries;
    private final AccountConfigurations accountConfigurations;
    private final StoreAccess storeAccess;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Optional<UUID> storeId = storeIdOf(pathOf(request));
        if (storeId.isEmpty()) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }
        check(storeId.get(), authentication.getName());
        return true;
    }

    /** Lanza 403 {@code STAFF_TWO_FACTOR_REQUIRED} si el almacén exige 2FA y la cuenta, del almacén, no la tiene. */
    void check(UUID storeId, String email) {
        if (!requiresStaffTwoFactor(storeId)) {
            return;
        }
        accountConfigurations.securityStatus(email)
                .filter(status -> !status.twoFactorEnabled())
                .filter(status -> storeAccess.isMember(status.personId(), storeId))
                .ifPresent(status -> {
                    throw new ForbiddenException(
                            "STAFF_TWO_FACTOR_REQUIRED",
                            "Este almacén exige verificación en dos pasos. Actívala en la configuración de tu cuenta.",
                            Map.of("storeId", storeId.toString())
                    );
                });
    }

    private boolean requiresStaffTwoFactor(UUID storeId) {
        try {
            return settingsQueries.requiresStaffTwoFactor(storeId);
        } catch (NotFoundException missingStore) {
            return false;
        }
    }

    /** Id del almacén de la ruta, salvo en las rutas exentas. */
    static Optional<UUID> storeIdOf(String path) {
        Matcher matcher = STORE_PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String rest = matcher.group(2);
        if (rest != null && (rest.equals(EXEMPT_SUFFIX) || rest.equals(EXEMPT_SUFFIX + "/"))) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }
}
