package com.autopartco.backend.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.web.TwoFactorResendResponse;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

/**
 * Segundo paso del login con 2FA por correo. El desafío es una fila {@code account_tokens} de propósito
 * {@code TWO_FACTOR_LOGIN} (su id es el {@code challengeId}) con el hash {@code sha256(accountId:code)}, válida 10
 * minutos y 5 intentos. Un reenvío crea un desafío nuevo (nuevo {@code challengeId}) e invalida el anterior.
 */
@Service
@AllArgsConstructor
public class TwoFactorLoginService {
    static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final int COOLDOWN_SECONDS = 60;
    static final int MAX_PER_HOUR = 5;
    static final String PURPOSE = EmailVerificationTokenService.PURPOSE_TWO_FACTOR_LOGIN;

    private static final String INVALID_CODE = "INVALID_TWO_FACTOR_CODE";
    private static final String INVALID_CODE_MESSAGE = "El código no es válido o expiró.";

    private final AccountTokenRepository accountTokenRepository;
    private final EmailVerificationTokenService tokenService;
    private final TokenMapper tokenMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final AccountStatusService accountStatusService;
    private final AuditService auditService;
    private final SessionOpener sessionOpener;

    public boolean isEnabled(Account account) {
        return AccountConfigurations.twoFactorEnabled(account.getConfiguration());
    }

    /**
     * Crea el desafío (invalida los anteriores) y pide enviar el código. Sin tokens ni sesión. Con la misma espera
     * ({@value #COOLDOWN_SECONDS} s desde el último código) y el mismo tope ({@value #MAX_PER_HOUR} por hora) que
     * {@link #resend}, por cuenta: no envía correo y devuelve el desafío vigente; si no queda ninguno usable, 429
     * {@code TWO_FACTOR_RATE_LIMITED}.
     */
    @Transactional
    public LoginResponse startChallenge(Account account) {
        LocalDateTime now = LocalDateTime.now();
        Optional<AccountToken> latest = accountTokenRepository
                .findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), PURPOSE);
        boolean cooling = latest.map(AccountToken::getCreatedAt)
                .map(createdAt -> createdAt.plusSeconds(COOLDOWN_SECONDS).isAfter(now))
                .orElse(false);
        if (cooling || accountTokenRepository.countByAccount_IdAndPurposeAndCreatedAtAfter(
                account.getId(), PURPOSE, now.minusHours(1)) >= MAX_PER_HOUR) {
            AccountToken current = latest
                    .filter(token -> !token.isUsed())
                    .filter(token -> token.getExpirationAt().isAfter(now))
                    .orElseThrow(() -> new TooManyRequestsException("TWO_FACTOR_RATE_LIMITED",
                            "Pediste demasiados códigos. Espera unos minutos e inicia sesión de nuevo."));
            return LoginResponse.twoFactorChallenge(current.getId(), toInstant(current.getExpirationAt()));
        }
        accountTokenRepository.markUsed(account.getId(), List.of(PURPOSE), LocalDateTime.now());
        AccountToken challenge = issue(account);
        auditService.record(account.getId(), "TWO_FACTOR_CHALLENGE_SENT", "ACCOUNT", account.getId(), Map.of());
        return LoginResponse.twoFactorChallenge(challenge.getId(), toInstant(challenge.getExpirationAt()));
    }

    /**
     * Valida el código y abre la sesión. Los fallos se persisten aunque se lance la excepción y cuentan como intento
     * fallido de login; al 5.º el desafío queda inválido (429).
     */
    @Transactional(noRollbackFor = {BadRequestException.class, TooManyRequestsException.class})
    public LoginResponse verify(UUID challengeId, String code, String ipAddress, String userAgent) {
        AccountToken challenge = activeChallenge(challengeId);
        Account account = challenge.getAccount();
        if (!canSignIn(account)) {
            challenge.setUsed(true);
            accountTokenRepository.save(challenge);
            throw invalidCode();
        }

        String supplied = tokenService.hashVerificationCode(account.getId(), code == null ? "" : code.trim());
        if (!MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                challenge.getToken().getBytes(StandardCharsets.UTF_8))) {
            registerFailure(challenge, account, ipAddress);
        }

        challenge.setUsed(true);
        accountTokenRepository.save(challenge);
        loginAttemptService.record(account, true, ipAddress);
        auditService.record(account.getId(), "LOGIN_SUCCEEDED", "ACCOUNT", account.getId(), metadata(ipAddress));
        return sessionOpener.open(account, ipAddress, userAgent);
    }

    /**
     * Reenvía el código: crea un desafío nuevo e invalida el anterior. Con menos de 60 s desde el último código o 5
     * en la última hora no envía y devuelve el desafío vigente.
     */
    @Transactional
    public TwoFactorResendResponse resend(UUID challengeId) {
        AccountToken current = activeChallenge(challengeId);
        Account account = current.getAccount();
        LocalDateTime now = LocalDateTime.now();

        long waitSeconds = accountTokenRepository
                .findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), PURPOSE)
                .map(AccountToken::getCreatedAt)
                .map(createdAt -> Duration.between(now, createdAt.plusSeconds(COOLDOWN_SECONDS)).toSeconds())
                .orElse(0L);
        if (waitSeconds > 0) {
            return new TwoFactorResendResponse(current.getId(), toInstant(current.getExpirationAt()), waitSeconds);
        }
        long lastHour = accountTokenRepository
                .countByAccount_IdAndPurposeAndCreatedAtAfter(account.getId(), PURPOSE, now.minusHours(1));
        if (lastHour >= MAX_PER_HOUR) {
            return new TwoFactorResendResponse(current.getId(), toInstant(current.getExpirationAt()), COOLDOWN_SECONDS);
        }

        accountTokenRepository.markUsed(account.getId(), List.of(PURPOSE), now);
        AccountToken challenge = issue(account);
        auditService.record(account.getId(), "TWO_FACTOR_CHALLENGE_RESENT", "ACCOUNT", account.getId(), Map.of());
        return new TwoFactorResendResponse(challenge.getId(), toInstant(challenge.getExpirationAt()), COOLDOWN_SECONDS);
    }

    private AccountToken issue(Account account) {
        String code = tokenService.newCode();
        AccountToken challenge = accountTokenRepository.save(tokenMapper.toAccountToken(
                account,
                PURPOSE,
                tokenService.hashVerificationCode(account.getId(), code),
                LocalDateTime.now().plus(CHALLENGE_VALIDITY)
        ));
        eventPublisher.publishEvent(new TwoFactorCodeRequested(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                code,
                "iniciar sesión",
                CHALLENGE_VALIDITY.toMinutes()
        ));
        return challenge;
    }

    private AccountToken activeChallenge(UUID challengeId) {
        if (challengeId == null) {
            throw invalidCode();
        }
        return accountTokenRepository.lockByIdAndPurpose(challengeId, PURPOSE)
                .filter(token -> !token.isUsed())
                .filter(token -> token.getExpirationAt().isAfter(LocalDateTime.now()))
                .orElseThrow(TwoFactorLoginService::invalidCode);
    }

    private void registerFailure(AccountToken challenge, Account account, String ipAddress) {
        loginAttemptService.record(account, false, ipAddress);
        auditService.record(account.getId(), "LOGIN_FAILED", "ACCOUNT", account.getId(),
                Map.of("reason", "TWO_FACTOR_CODE"));
        short attempts = (short) (challenge.getAttempts() + 1);
        challenge.setAttempts(attempts);
        boolean lockAccount = loginAttemptService.hasExceededFailedAttempts(account.getId());
        if (lockAccount) {
            accountStatusService.lock(account, ipAddress, LoginAttemptService.MAX_FAILED_ATTEMPTS);
        }
        if (attempts >= MAX_ATTEMPTS || lockAccount) {
            challenge.setUsed(true);
            accountTokenRepository.save(challenge);
            throw new TooManyRequestsException(
                    "TWO_FACTOR_ATTEMPTS_EXCEEDED",
                    "Superaste el número de intentos. Inicia sesión de nuevo."
            );
        }
        accountTokenRepository.save(challenge);
        throw invalidCode();
    }

    private boolean canSignIn(Account account) {
        return account.getState() != null
                && StateCodes.ACTIVE.equals(account.getState().getCode())
                && !accountStatusService.isLocked(account);
    }

    private static Map<String, Object> metadata(String ipAddress) {
        return ipAddress == null ? Map.of("twoFactor", true) : Map.of("ipAddress", ipAddress, "twoFactor", true);
    }

    private static java.time.Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static BadRequestException invalidCode() {
        return new BadRequestException(INVALID_CODE, INVALID_CODE_MESSAGE);
    }
}
