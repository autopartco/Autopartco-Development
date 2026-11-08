package com.autopartco.backend.core.identity.internal;

import com.autopartco.backend.core.audit.AuditService;
import java.util.Map;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.web.LoginRequest;
import com.autopartco.backend.core.identity.LoginResponse;
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
    private final AuthTokenService authTokenService;
    private final AccountSessionService accountSessionService;
    private final AuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> invalidCredentials());

        if (accountStatusService.isLocked(account)) {
            throw invalidCredentials();
        }

        if (account.getState() == null
                || !StateCodes.ACTIVE.equals(account.getState().getCode())) {
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

        loginAttemptService.record(account, true, ipAddress);
        auditService.record(account.getId(), "LOGIN_SUCCEEDED", "ACCOUNT", account.getId(), ipMetadata(ipAddress));
        AuthTokenService.TokenPair tokens = authTokenService.issue(account);
        accountSessionService.create(
                tokens.sessionId(),
                account,
                tokens.response().refreshToken(),
                ipAddress,
                userAgent,
                tokens.refreshExpiresAt()
        );

        return tokens.response();
    }

    private static Map<String, Object> ipMetadata(String ipAddress) {
        return ipAddress == null ? Map.of() : Map.of("ipAddress", ipAddress);
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("UNAUTHORIZED", "Las credenciales no son válidas.");
    }

}
