package com.autopartco.backend.shared.security;

import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.web.ApiPaths;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad HTTP de la API (sección 6 del diseño modular).
 *
 * <ol>
 *   <li>Rutas públicas: lista explícita (método + ruta) en {@link #PUBLIC_ROUTES}. Son exactamente las que
 *       eran públicas antes del refactor modular, con su ruta nueva.</li>
 *   <li>{@code /api/v1/admin/**}: solo {@code ROLE_ROOT}, además de los {@code @PreAuthorize} de cada
 *       controlador (defensa en profundidad).</li>
 *   <li>Todo lo demás: autenticado. La autorización por almacén o recurso la resuelven los casos de uso.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
public class WebSecurityConfig {

        /** Ruta pública: método HTTP y patrón de ruta. */
        public record PublicRoute(HttpMethod method, String pattern) {
        }

        public static final String ADMIN_MATCHER = ApiPaths.ADMIN + "/**";
        public static final String ROOT_AUTHORITY = "ROLE_ROOT";
        public static final String HEALTH_ENDPOINT = "/actuator/health";

        public static final List<PublicRoute> PUBLIC_ROUTES = List.of(
                // core.identity: flujos sin sesión (logout y logout/others siguen autenticados).
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/register"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/login"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/refresh"),
                new PublicRoute(HttpMethod.GET, ApiPaths.BASE + "/auth/verify-email"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/unlock/request"),
                new PublicRoute(HttpMethod.GET, ApiPaths.BASE + "/auth/unlock"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/password-recovery/request"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/auth/password-recovery/reset"),
                // core.billing: webhook de Wompi (se valida con firma).
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/webhooks/wompi"),
                // storesuite.onboarding: solicitud de almacén y configuración con token.
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/store-applications"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/store-applications/setup"),
                // storesuite.staff: invitaciones con token.
                new PublicRoute(HttpMethod.GET, ApiPaths.BASE + "/staff-invitations/preview"),
                new PublicRoute(HttpMethod.POST, ApiPaths.BASE + "/staff-invitations/accept"),
                // storesuite.subscription: catálogo de planes.
                new PublicRoute(HttpMethod.GET, ApiPaths.BASE + "/plans"),
                // marketplace: navegación de compradores.
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/stores"),
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/stores/*/brand/*"),
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/products"),
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/products/*"),
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/product-options"),
                new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/product-options/*")
        );

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, UnauthorizedEntryPoint unauthorizedEntryPoint, ForbiddenHandler forbiddenHandler) throws Exception {

                return httpSecurity
                        .csrf(csrf -> csrf.disable())
                        .cors(Customizer.withDefaults())

                        .sessionManagement(session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        )

                        .exceptionHandling(exception -> exception
                                .authenticationEntryPoint(unauthorizedEntryPoint)
                                .accessDeniedHandler(forbiddenHandler)
                        )

                        .authorizeHttpRequests(auth -> {
                                PUBLIC_ROUTES.forEach(route ->
                                        auth.requestMatchers(route.method(), route.pattern()).permitAll());
                                auth.requestMatchers(ADMIN_MATCHER).hasAuthority(ROOT_AUTHORITY)
                                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                        .requestMatchers(HEALTH_ENDPOINT).permitAll()
                                        .anyRequest().authenticated();
                        })

                        //  Configuracion de recursos para usar JWT
                        .oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        )

                        .formLogin(form -> form.disable())

                        .httpBasic(basic -> basic.disable())

                        .build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter authoritiesConverter =
                        new JwtGrantedAuthoritiesConverter();
                authoritiesConverter.setAuthoritiesClaimName("roles");
                authoritiesConverter.setAuthorityPrefix("");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
                return converter;
        }

        @Bean
        public JwtEncoder jwtEncoder(RSAPublicKey pub, RSAPrivateKey pri) {
                JWK jwk = new RSAKey.Builder(pub).privateKey(pri).build();
                JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
                return new NimbusJwtEncoder(jwks);
        }

        @Bean
        public JwtDecoder jwtDecoder(RSAPublicKey pub) {
                return NimbusJwtDecoder.withPublicKey(pub).build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
