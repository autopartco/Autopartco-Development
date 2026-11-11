package com.autopartco.backend.core.identity.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.RegisterRequest;
import com.autopartco.backend.core.identity.RegisterResponse;
import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.AccountUnlockService;
import com.autopartco.backend.core.identity.internal.EmailVerificationService;
import com.autopartco.backend.core.identity.internal.LoginService;
import com.autopartco.backend.core.identity.internal.social.SocialLoginService;
import com.autopartco.backend.core.identity.internal.social.SocialProvider;
import com.autopartco.backend.core.identity.internal.PasswordRecoveryService;
import com.autopartco.backend.core.identity.internal.ResendVerificationService;
import com.autopartco.backend.core.identity.internal.RegistrationService;
import com.autopartco.backend.core.identity.internal.SessionRefreshService;
import com.autopartco.backend.core.identity.internal.TwoFactorLoginService;
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
    private final SocialLoginService socialLoginService;
    private final EmailVerificationService emailVerificationService;
    private final AccountUnlockService accountUnlockService;
    private final AccountSessionService accountSessionService;
    private final SessionRefreshService sessionRefreshService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final ResendVerificationService resendVerificationService;
    private final TwoFactorLoginService twoFactorLoginService;
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

    /** Segundo paso del login con 2FA por correo: valida el código del desafío y emite la sesión como {@code /login}. */
    @PostMapping("/login/two-factor")
    public ResponseEntity<LoginResponse> loginTwoFactor(
            @Valid @RequestBody TwoFactorLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse tokens = twoFactorLoginService.verify(
                request.challengeId(), request.code(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return withRefreshCookie(tokens);
    }

    /** Reenvía el código 2FA: crea un desafío nuevo (nuevo {@code challengeId}) salvo que deba esperar. */
    @PostMapping("/login/two-factor/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TwoFactorResendResponse resendTwoFactor(@Valid @RequestBody TwoFactorChallengeRequest request) {
        return twoFactorLoginService.resend(request.challengeId());
    }

    /** Ingreso con Google o Facebook ({@code provider} = {@code google} | {@code facebook}). Ver {@link SocialLoginService}. */
    @PostMapping("/social/{provider}")
    public ResponseEntity<LoginResponse> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResponse tokens = socialLoginService.login(
                SocialProvider.fromPath(provider), request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
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

    @PostMapping("/verify-email/code")
    public VerifyEmailResponse verifyEmailByCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
        return emailVerificationService.verifyByCode(request);
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResendVerificationResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return resendVerificationService.resend(request);
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

    /**
     * Devuelve el access token en el cuerpo y el refresh token solo en la cookie HttpOnly. Si el login exige 2FA no
     * hay tokens: se devuelve el desafío sin cookie.
     */
    private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse tokens) {
        if (tokens.twoFactorRequired()) {
            return ResponseEntity.ok(tokens);
        }
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
