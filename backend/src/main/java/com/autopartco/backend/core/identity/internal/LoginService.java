package com.autopartco.backend.core.identity.internal;

import com.autopartco.backend.core.audit.AuditService;
import java.util.Map;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.web.LoginRequest;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class LoginService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AccountStatusService accountStatusService;
    private final AuditService auditService;
    private final TwoFactorLoginService twoFactorLoginService;
    private final SessionOpener sessionOpener;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> invalidCredentials());

        if (accountStatusService.isLocked(account)) {
            throw invalidCredentials();
        }

        String stateCode = account.getState() == null ? null : account.getState().getCode();
        boolean pendingVerification = StateCodes.PENDING.equals(stateCode);
        if (!pendingVerification && !StateCodes.ACTIVE.equals(stateCode)) {
            throw invalidCredentials();
        }

        if (loginAttemptService.hasExceededFailedAttempts(account.getId())) {
            accountStatusService.lock(account, ipAddress, 5);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            loginAttemptService.record(account, false, ipAddress);
            auditService.record(account.getId(), "LOGIN_FAILED", "ACCOUNT", account.getId(), ipMetadata(ipAddress));

            if (loginAttemptService.hasExceededFailedAttempts(account.getId())) {
                accountStatusService.lock(
                        account,
                        ipAddress,
                        LoginAttemptService.MAX_FAILED_ATTEMPTS
                );
            }

            throw invalidCredentials();
        }

        if (pendingVerification) {
            // Solo quien conoce la contraseña sabe que falta verificar el correo; no se registra como sesión.
            throw new ForbiddenException(
                    "EMAIL_NOT_VERIFIED",
                    "Debes verificar tu correo para iniciar sesión.",
                    Map.of("email", maskEmail(account.getEmail()))
            );
        }

        if (twoFactorLoginService.isEnabled(account)) {
            // Contraseña correcta, pero sin sesión hasta validar el código: el intento exitoso se registra después.
            return twoFactorLoginService.startChallenge(account);
        }

        loginAttemptService.record(account, true, ipAddress);
        auditService.record(account.getId(), "LOGIN_SUCCEEDED", "ACCOUNT", account.getId(), ipMetadata(ipAddress));
        return sessionOpener.open(account, ipAddress, userAgent);
    }

    private static Map<String, Object> ipMetadata(String ipAddress) {
        return ipAddress == null ? Map.of() : Map.of("ipAddress", ipAddress);
    }

    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("UNAUTHORIZED", "Las credenciales no son válidas.");
    }

}
