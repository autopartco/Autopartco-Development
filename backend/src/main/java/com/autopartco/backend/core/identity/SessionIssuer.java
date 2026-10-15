package com.autopartco.backend.core.identity;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.AuthTokenService;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Emite tokens y abre una sesión para una cuenta ya autorizada por otro medio (por ejemplo, el token de
 * configuración del almacén). Usa la misma emisión y el mismo registro de sesión que el login.
 */
@Service
@AllArgsConstructor
public class SessionIssuer {
    private final AccountRepository accountRepository;
    private final AuthTokenService authTokenService;
    private final AccountSessionService accountSessionService;

    @Transactional
    public LoginResponse issue(UUID accountId, String ipAddress, String userAgent) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
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
}
