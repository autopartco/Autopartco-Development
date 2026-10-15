package com.autopartco.backend.core.identity.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.RegisterRequest;
import com.autopartco.backend.core.identity.RegisterResponse;
import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.AccountUnlockService;
import com.autopartco.backend.core.identity.internal.EmailVerificationService;
import com.autopartco.backend.core.identity.internal.LoginService;
import com.autopartco.backend.core.identity.internal.PasswordRecoveryService;
import com.autopartco.backend.core.identity.internal.RegistrationService;
import com.autopartco.backend.core.identity.internal.SessionRefreshService;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Flujo sin sesión y gestión del token: registro, login, refresh, logout, verificación, desbloqueo y
 * recuperación de contraseña. El registro de vendedor está en {@code storesuite.onboarding}
 * ({@code POST /store-applications}).
 *
 * <p>El refresh token viaja en la cookie {@link RefreshCookie#NAME}, no en el cuerpo de la respuesta.
 * Login y refresh la renuevan; logout la borra. Refresh y logout también aceptan el token en el cuerpo.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/auth")
@AllArgsConstructor
public class AuthController {
    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final EmailVerificationService emailVerificationService;
    private final AccountUnlockService accountUnlockService;
    private final AccountSessionService accountSessionService;
    private final SessionRefreshService sessionRefreshService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final RefreshCookie refreshCookie;

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse tokens = loginService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return withRefreshCookie(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken
    ) {
        String token = firstPresent(request == null ? null : request.refreshToken(), cookieToken);
        if (token == null) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "El refresh token no es válido.");
        }
        return withRefreshCookie(sessionRefreshService.refresh(token));
    }

    @GetMapping("/verify-email")
    public VerifyEmailResponse verifyEmail(@RequestParam String token) {
        return emailVerificationService.verify(token);
    }

    @PostMapping("/unlock/request")
    public UnlockAccountResponse requestUnlock(@Valid @RequestBody UnlockAccountRequest request) {
        return accountUnlockService.requestUnlock(request);
    }

    @GetMapping("/unlock")
    public UnlockAccountResponse unlock(@RequestParam String token) {
        return accountUnlockService.unlock(token);
    }

    @PostMapping("/password-recovery/request")
    public PasswordRecoveryResponse requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        return passwordRecoveryService.requestRecovery(request);
    }

    @PostMapping("/password-recovery/reset")
    public PasswordRecoveryResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return passwordRecoveryService.resetPassword(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            Authentication authentication
    ) {
        String token = firstPresent(request == null ? null : request.refreshToken(), cookieToken);
        if (token != null) {
            accountSessionService.revokeByRefreshToken(token, authentication.getName());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }

    @PostMapping("/logout/others")
    public ResponseEntity<Void> logoutOtherSessions(
            @RequestBody(required = false) LogoutRequest request,
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            Authentication authentication
    ) {
        String token = firstPresent(request == null ? null : request.refreshToken(), cookieToken);
        if (token != null) {
            accountSessionService.revokeOthersByRefreshToken(token, authentication.getName());
        }
        return ResponseEntity.noContent().build();
    }

    /** Devuelve el access token en el cuerpo y el refresh token solo en la cookie HttpOnly. */
    private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(tokens.refreshToken()))
                .body(new LoginResponse(tokens.accessToken(), null, tokens.tokenType(), tokens.expiresIn()));
    }

    private static String firstPresent(String fromBody, String fromCookie) {
        if (fromBody != null && !fromBody.isBlank()) return fromBody.trim();
        if (fromCookie != null && !fromCookie.isBlank()) return fromCookie.trim();
        return null;
    }
}
