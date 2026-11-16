package com.autopartco.backend.operations.console;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.security.PlatformAccess;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/**
 * Acceso de quien usa la consola: si es {@code ROOT} y los permisos {@code PLATFORM_*} de su token. La interfaz lo usa
 * para mostrar solo las secciones permitidas; el servidor vuelve a comprobar cada endpoint.
 */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/me")
@AllArgsConstructor
public class AdminMeController {
    private final JdbcClient jdbc;

    public record AdminMe(String email, boolean root, String roleName, List<String> permissions) {
    }

    @GetMapping
    @PreAuthorize(PlatformAccess.ANY)
    public AdminMe me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        boolean root = authorities.contains("ROLE_ROOT");
        String roleName = root ? "Root" : jdbc.sql("""
                SELECT r.name FROM accounts a
                JOIN platform_staff s ON s.account_id = a.id AND s.deleted_at IS NULL
                JOIN platform_roles r ON r.id = s.role_id
                WHERE lower(a.email) = lower(:email)
                """).param("email", authentication.getName()).query(String.class).optional().orElse(null);
        List<String> permissions = authorities.stream().filter(a -> a.startsWith("PLATFORM_")).sorted().toList();
        return new AdminMe(authentication.getName(), root, roleName, permissions);
    }
}
