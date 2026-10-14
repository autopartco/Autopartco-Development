package com.autopartco.backend.core.identity.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Flujo sin sesión y gestión del token: registro, login, refresh, logout, verificación, desbloqueo y
 * recuperación de contraseña. El registro de vendedor está en {@code storesuite.onboarding}
 * ({@code POST /store-applications}).
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

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return loginService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return sessionRefreshService.refresh(request.refreshToken());
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
            @Valid @RequestBody LogoutRequest request,
            Authentication authentication
    ) {
        accountSessionService.revokeByRefreshToken(
                request.refreshToken(),
                authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout/others")
    public ResponseEntity<Void> logoutOtherSessions(
            @Valid @RequestBody LogoutRequest request,
            Authentication authentication
    ) {
        accountSessionService.revokeOthersByRefreshToken(
                request.refreshToken(),
                authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }
}
