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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
public class WebSecurityConfig {

        public final static String LOGIN_URL_MATCHER = ApiPaths.BASE + "/auth/login";
        public final static String REFRESH_URL_MATCHER = ApiPaths.BASE + "/auth/refresh";
        public final static String REGISTER_URL_MATCHER = ApiPaths.BASE + "/auth/register";
        public final static String SELLER_REGISTER_URL_MATCHER = ApiPaths.BASE + "/auth/register/seller";
        public final static String VERIFY_EMAIL_URL_MATCHER = ApiPaths.BASE + "/auth/verify-email";
        public final static String REQUEST_UNLOCK_URL_MATCHER = ApiPaths.BASE + "/auth/unlock/request";
        public final static String UNLOCK_URL_MATCHER = ApiPaths.BASE + "/auth/unlock";
        public final static String PASSWORD_RECOVERY_REQUEST_URL_MATCHER =
                ApiPaths.BASE + "/auth/password-recovery/request";
        public final static String PASSWORD_RECOVERY_RESET_URL_MATCHER =
                ApiPaths.BASE + "/auth/password-recovery/reset";
        public final static String LOG_OUT_MATCHER = ApiPaths.BASE + "/auth/logout";
        public final static String SELLER_SETUP_MATCHER = ApiPaths.BASE + "/seller-applications/setup";
        public final static String SELLER_PLANS_MATCHER = ApiPaths.BASE + "/plans";
        public final static String SELLER_BRAND_MATCHER = ApiPaths.BASE + "/seller-applications/stores/*/brand/*";
        public final static String WOMPI_WEBHOOK_MATCHER = ApiPaths.BASE + "/webhooks/wompi";
        public final static String PUBLISHED_STORES_MATCHER = ApiPaths.BASE + "/stores/published";
        public final static String PUBLISHED_PRODUCTS_MATCHER = ApiPaths.BASE + "/products/published";
        public final static String PRODUCT_DETAIL_MATCHER = ApiPaths.BASE + "/products/*";
        public final static String PRODUCT_OPTIONS_MATCHER = ApiPaths.BASE + "/products/options";
        final String BASE_URL_MATCHER = ApiPaths.BASE + "/**";

        final String[] PUBLIC_ENDPOINTS = {
                "/actuator/health"
        };

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

                        .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.POST, LOGIN_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REFRESH_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REGISTER_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, SELLER_REGISTER_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, VERIFY_EMAIL_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, REQUEST_UNLOCK_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, UNLOCK_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, PASSWORD_RECOVERY_REQUEST_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, PASSWORD_RECOVERY_RESET_URL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, SELLER_SETUP_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, WOMPI_WEBHOOK_MATCHER).permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        ApiPaths.BASE + "/seller-dashboard/invitations/preview")
                                        .permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        ApiPaths.BASE + "/seller-dashboard/invitations/accept")
                                        .permitAll()
                                .requestMatchers(HttpMethod.GET, SELLER_PLANS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, SELLER_BRAND_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.POST, LOG_OUT_MATCHER).authenticated()
                                .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/stores/*/status").authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiPaths.BASE + "/stores/*").authenticated()
                                .requestMatchers(HttpMethod.GET, PUBLISHED_STORES_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLISHED_PRODUCTS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PRODUCT_DETAIL_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, PRODUCT_OPTIONS_MATCHER).permitAll()
                                .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/products/options/*")
                                        .permitAll()
                                .requestMatchers(ApiPaths.BASE + "/categories/**").hasAuthority("ROLE_ROOT")
                                .requestMatchers(ApiPaths.BASE + "/manufacturers/**").hasAuthority("ROLE_ROOT")
                                .requestMatchers(
                                        ApiPaths.BASE + "/platform/promotions",
                                        ApiPaths.BASE + "/platform/promotions/**")
                                        .hasAuthority("ROLE_ROOT")
                                .requestMatchers(
                                        ApiPaths.BASE + "/stores/*/promotions",
                                        ApiPaths.BASE + "/stores/*/promotions/**")
                                        .authenticated()
                                .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/stores/*/branches")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/stores/*/branches")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiPaths.BASE + "/stores/*/branches/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiPaths.BASE + "/stores/*/branches/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/stores/*/products")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/stores/*/products")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiPaths.BASE + "/stores/*/branches/*/products/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiPaths.BASE + "/stores/*/products/*/publication")
                                        .authenticated()
                                .requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/stores/*/products/*/images")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PUT, ApiPaths.BASE + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.PATCH, ApiPaths.BASE + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiPaths.BASE + "/stores/*/products/*/images/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.DELETE, ApiPaths.BASE + "/stores/*/products/*")
                                        .authenticated()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                                .anyRequest().authenticated()
                        )

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
