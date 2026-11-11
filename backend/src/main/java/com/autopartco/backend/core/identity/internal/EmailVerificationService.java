package com.autopartco.backend.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.web.VerifyEmailCodeRequest;
import com.autopartco.backend.core.identity.web.VerifyEmailResponse;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmailVerificationService {
    static final int MAX_CODE_ATTEMPTS = 5;

    private static final String VERIFIED_STATE = StateCodes.ACTIVE;
    private static final String INVALID_CODE = "INVALID_VERIFICATION_CODE";
    private static final String INVALID_CODE_MESSAGE = "El código no es válido o expiró.";

    private final AccountRepository accountRepository;
    private final StateRepository stateRepository;
    private final EmailVerificationTokenService tokenService;
    private final AccountTokenRepository accountTokenRepository;
    private final AuditService auditService;
    private final AuthResponseMapper authResponseMapper;

    @Transactional
    public VerifyEmailResponse verify(String rawToken) {
        var accountToken = tokenService.findValidVerificationToken(rawToken);

        State activeState = activeState();

        Account account = accountToken.getAccount();
        account.setState(activeState);
        tokenService.invalidatePendingVerification(account.getId());
        accountToken.setUsed(true);

        accountRepository.save(account);
        tokenService.save(accountToken);
        auditService.recordEmailVerified(account.getId(), account.getEmail(), activeState.getCode());

        return authResponseMapper.toVerifyEmailResponse(account, activeState);
    }

    /**
     * Verifica con el código de 6 dígitos. Los fallos se persisten aunque se lance la excepción
     * ({@code noRollbackFor}); la cuenta se bloquea con {@code lockByEmail} para que dos intentos simultáneos
     * no se salten el límite.
     */
    @Transactional(noRollbackFor = {BadRequestException.class, TooManyRequestsException.class})
    public VerifyEmailResponse verifyByCode(VerifyEmailCodeRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.lockByEmail(email)
                .filter(found -> found.getState() != null
                        && StateCodes.PENDING.equals(found.getState().getCode()))
                .orElseThrow(EmailVerificationService::invalidCode);

        List<AccountToken> active = accountTokenRepository.findActive(
                account.getId(),
                EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION_CODE,
                LocalDateTime.now()
        );
        if (active.isEmpty()) {
            throw invalidCode();
        }

        AccountToken codeToken = active.get(0);
        String supplied = tokenService.hashVerificationCode(account.getId(), request.code().trim());
        if (!MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                codeToken.getToken().getBytes(StandardCharsets.UTF_8))) {
            registerFailedAttempt(codeToken);
        }

        State activeState = activeState();
        account.setState(activeState);
        tokenService.invalidatePendingVerification(account.getId());
        accountRepository.save(account);
        auditService.recordEmailVerified(account.getId(), account.getEmail(), activeState.getCode());

        return authResponseMapper.toVerifyEmailResponse(account, activeState);
    }

    private void registerFailedAttempt(AccountToken codeToken) {
        short attempts = (short) (codeToken.getAttempts() + 1);
        codeToken.setAttempts(attempts);
        if (attempts >= MAX_CODE_ATTEMPTS) {
            codeToken.setUsed(true);
            tokenService.save(codeToken);
            throw new TooManyRequestsException(
                    "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "Superaste el número de intentos. Solicita un nuevo código."
            );
        }
        tokenService.save(codeToken);
        throw invalidCode();
    }

    private State activeState() {
        return stateRepository.findByCode(VERIFIED_STATE)
                .orElseThrow(() -> new NotFoundException("STATE_NOT_FOUND", "No existe el estado activo."));
    }

    private static BadRequestException invalidCode() {
        return new BadRequestException(INVALID_CODE, INVALID_CODE_MESSAGE);
    }
}
