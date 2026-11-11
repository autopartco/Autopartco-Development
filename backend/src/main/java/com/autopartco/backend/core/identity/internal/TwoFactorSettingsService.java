package com.autopartco.backend.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.identity.internal.social.AccountIdentityRepository;
import com.autopartco.backend.core.identity.web.TwoFactorSetupResponse;
import com.autopartco.backend.core.identity.web.TwoFactorStatusResponse;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

import lombok.AllArgsConstructor;

/**
 * Activar y desactivar la 2FA por correo ({@code security.twoFactor.enabled}). Activar exige un código enviado al
 * correo ({@code TWO_FACTOR_SETUP}, 10 min, 5 intentos, espera de 60 s y máx. 5 por hora). Desactivar exige la
 * contraseña o, en cuentas con ingreso social (pueden no conocer su contraseña), el mismo flujo de código con
 * {@code action=DISABLE}. La acción va dentro del hash: un código de activación no sirve para desactivar.
 */
@Service
@AllArgsConstructor
public class TwoFactorSettingsService {
    public static final String ENABLE = "ENABLE";
    public static final String DISABLE = "DISABLE";

    static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final int COOLDOWN_SECONDS = 60;
    static final int MAX_PER_HOUR = 5;
    static final String PURPOSE = EmailVerificationTokenService.PURPOSE_TWO_FACTOR_SETUP;

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailVerificationTokenService tokenService;
    private final TokenMapper tokenMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final AccountIdentityRepository identityRepository;
    private final AuditService auditService;
    private final TwoFactorDisableAttempts disableAttempts;

    @Transactional
    public TwoFactorSetupResponse request(String email, String action) {
        Account account = lock(email);
        boolean enabled = AccountConfigurations.twoFactorEnabled(account.getConfiguration());
        if (ENABLE.equals(action) && enabled) {
            throw new BadRequestException("TWO_FACTOR_ALREADY_ENABLED", "La verificación en dos pasos ya está activa.");
        }
        if (DISABLE.equals(action)) {
            requireEnabled(enabled);
            if (!identityRepository.existsByAccount_Id(account.getId())) {
                throw new BadRequestException("PASSWORD_REQUIRED",
                        "Para desactivar la verificación en dos pasos confirma tu contraseña.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        long waitSeconds = accountTokenRepository
                .findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), PURPOSE)
                .map(AccountToken::getCreatedAt)
                .map(createdAt -> Duration.between(now, createdAt.plusSeconds(COOLDOWN_SECONDS)).toSeconds())
                .orElse(0L);
        if (waitSeconds > 0) {
            return new TwoFactorSetupResponse(false, waitSeconds, CODE_VALIDITY.toMinutes());
        }
        if (accountTokenRepository.countByAccount_IdAndPurposeAndCreatedAtAfter(
                account.getId(), PURPOSE, now.minusHours(1)) >= MAX_PER_HOUR) {
            return new TwoFactorSetupResponse(false, COOLDOWN_SECONDS, CODE_VALIDITY.toMinutes());
        }

        accountTokenRepository.markUsed(account.getId(), List.of(PURPOSE), now);
        String code = tokenService.newCode();
        accountTokenRepository.save(tokenMapper.toAccountToken(
                account,
                PURPOSE,
                hash(account, action, code),
                now.plus(CODE_VALIDITY)
        ));
        eventPublisher.publishEvent(new TwoFactorCodeRequested(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                code,
                ENABLE.equals(action) ? "activar la verificación en dos pasos" : "desactivar la verificación en dos pasos",
                CODE_VALIDITY.toMinutes()
        ));
        return new TwoFactorSetupResponse(true, COOLDOWN_SECONDS, CODE_VALIDITY.toMinutes());
    }

    /** Confirma el código y aplica la acción. Los fallos se persisten; al 5.º el código queda inválido (429). */
    @Transactional(noRollbackFor = {BadRequestException.class, TooManyRequestsException.class})
    public TwoFactorStatusResponse confirm(String email, String code, String action) {
        Account account = lock(email);
        List<AccountToken> active = accountTokenRepository.findActive(account.getId(), PURPOSE, LocalDateTime.now());
        if (active.isEmpty()) {
            throw invalidCode();
        }
        AccountToken token = active.get(0);
        String supplied = hash(account, action, code == null ? "" : code.trim());
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                token.getToken().getBytes(StandardCharsets.UTF_8))) {
            short attempts = (short) (token.getAttempts() + 1);
            token.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                token.setUsed(true);
                accountTokenRepository.save(token);
                throw new TooManyRequestsException("TWO_FACTOR_ATTEMPTS_EXCEEDED",
                        "Superaste el número de intentos. Solicita un nuevo código.");
            }
            accountTokenRepository.save(token);
            throw invalidCode();
        }
        token.setUsed(true);
        accountTokenRepository.save(token);
        return apply(account, ENABLE.equals(action));
    }

    /**
     * Desactiva con la contraseña de la cuenta. Con {@value TwoFactorDisableAttempts#MAX_FAILURES} contraseñas
     * erradas en una hora responde 429 {@code TWO_FACTOR_DISABLE_LOCKED} ({@link TwoFactorDisableAttempts}).
     */
    @Transactional
    public TwoFactorStatusResponse disableWithPassword(String email, String password) {
        Account account = lock(email);
        requireEnabled(AccountConfigurations.twoFactorEnabled(account.getConfiguration()));
        disableAttempts.requireNotLocked(account.getId());
        if (password == null || !passwordEncoder.matches(password, account.getPassword())) {
            disableAttempts.recordFailure(account.getId());
            auditService.record(account.getId(), "TWO_FACTOR_DISABLE_FAILED", "ACCOUNT", account.getId(), Map.of());
            throw new BadRequestException("INVALID_PASSWORD", "La contraseña no es correcta.");
        }
        return apply(account, false);
    }

    private TwoFactorStatusResponse apply(Account account, boolean enabled) {
        account.setConfiguration(AccountConfigurations.withTwoFactor(account.getConfiguration(), enabled));
        accountRepository.save(account);
        auditService.record(account.getId(), enabled ? "TWO_FACTOR_ENABLED" : "TWO_FACTOR_DISABLED", "ACCOUNT",
                account.getId(), Map.of("method", AccountConfigurations.EMAIL_METHOD));
        return new TwoFactorStatusResponse(enabled, AccountConfigurations.EMAIL_METHOD);
    }

    private String hash(Account account, String action, String code) {
        return tokenService.hashVerificationCode(account.getId(), action + ":" + code);
    }

    private static void requireEnabled(boolean enabled) {
        if (!enabled) {
            throw new BadRequestException("TWO_FACTOR_NOT_ENABLED", "La verificación en dos pasos no está activa.");
        }
    }

    private Account lock(String email) {
        return accountRepository.lockByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    private static BadRequestException invalidCode() {
        return new BadRequestException("INVALID_TWO_FACTOR_CODE", "El código no es válido o expiró.");
    }
}
