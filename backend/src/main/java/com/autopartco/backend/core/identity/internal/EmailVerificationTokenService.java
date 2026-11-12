package com.autopartco.backend.core.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmailVerificationTokenService {
    public static final String PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PURPOSE_EMAIL_VERIFICATION_CODE = "EMAIL_VERIFICATION_CODE";
    public static final String PURPOSE_ACCOUNT_UNLOCK = "ACCOUNT_UNLOCK";
    public static final String PURPOSE_PASSWORD_RECOVERY = "PASSWORD_RECOVERY";
    public static final String PURPOSE_TWO_FACTOR_LOGIN = "TWO_FACTOR_LOGIN";
    public static final String PURPOSE_TWO_FACTOR_SETUP = "TWO_FACTOR_SETUP";
    public static final String PURPOSE_EMAIL_CHANGE = "EMAIL_CHANGE";

    private static final Duration MAX_CODE_VALIDITY = Duration.ofMinutes(30);

    private static final int TOKEN_BYTES = 32;

    private final AccountTokenRepository accountTokenRepository;
    private final AppProperties appProperties;
    private final TokenMapper tokenMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CreatedEmailVerificationToken create(Account account) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(appProperties.emailVerification().expiresIn());

        AccountToken accountToken = tokenMapper.toAccountToken(
            account,
            PURPOSE_EMAIL_VERIFICATION,
            tokenHash,
            expiresAt
        );

        accountTokenRepository.save(accountToken);

        return new CreatedEmailVerificationToken(rawToken, expiresAt);
    }

    /** Vigencia del código de 6 dígitos: la del enlace, con tope de {@link #MAX_CODE_VALIDITY}. */
    public Duration codeValidity() {
        Duration linkValidity = appProperties.emailVerification().expiresIn();
        return linkValidity.compareTo(MAX_CODE_VALIDITY) < 0 ? linkValidity : MAX_CODE_VALIDITY;
    }

    /** Crea el enlace y el código de 6 dígitos de una verificación de correo. */
    public IssuedVerification issueVerification(Account account) {
        CreatedEmailVerificationToken link = create(account);
        String code = newCode();
        LocalDateTime codeExpiresAt = LocalDateTime.now().plus(codeValidity());
        accountTokenRepository.save(tokenMapper.toAccountToken(
                account,
                PURPOSE_EMAIL_VERIFICATION_CODE,
                hashVerificationCode(account.getId(), code),
                codeExpiresAt
        ));
        return new IssuedVerification(link.rawToken(), link.expiresAt(), code, codeExpiresAt);
    }

    /** Código de 6 dígitos con {@link SecureRandom}. */
    public String newCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    /** SHA-256 de {@code accountId:code}: el id de cuenta hace de sal para que el hash no sirva entre cuentas. */
    public String hashVerificationCode(UUID accountId, String code) {
        return hash(accountId + ":" + code);
    }

    /** Marca como usados el enlace y el código pendientes de la cuenta. */
    public void invalidatePendingVerification(UUID accountId) {
        accountTokenRepository.markUsed(
                accountId,
                List.of(PURPOSE_EMAIL_VERIFICATION, PURPOSE_EMAIL_VERIFICATION_CODE),
                LocalDateTime.now()
        );
    }

    public String verificationLink(String rawToken) {
        return UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public AccountToken findValidVerificationToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("INVALID_EMAIL_VERIFICATION_TOKEN", "El token de verificación es obligatorio.");
        }

        String tokenHash = hash(rawToken.trim());
        AccountToken accountToken = accountTokenRepository
                .findByTokenAndPurpose(tokenHash, PURPOSE_EMAIL_VERIFICATION)
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_EMAIL_VERIFICATION_TOKEN",
                    "El token de verificación no es válido."));

        if (accountToken.isUsed()) {
            throw new BadRequestException("EMAIL_VERIFICATION_TOKEN_USED", "El token de verificación ya fue utilizado.");
        }

        if (accountToken.getExpirationAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("EMAIL_VERIFICATION_TOKEN_EXPIRED", "El token de verificación expiró.");
        }

        return accountToken;
    }

    public AccountToken save(AccountToken accountToken) {
        return accountTokenRepository.save(accountToken);
    }

    public CreatedEmailVerificationToken createAccountUnlockToken(Account account) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(appProperties.emailVerification().expiresIn());

        accountTokenRepository.save(tokenMapper.toAccountToken(
                account,
                PURPOSE_ACCOUNT_UNLOCK,
                hash(rawToken),
                expiresAt
        ));

        return new CreatedEmailVerificationToken(rawToken, expiresAt);
    }

    public AccountToken findValidAccountUnlockToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("INVALID_ACCOUNT_UNLOCK_TOKEN", "El token de desbloqueo es obligatorio.");
        }

        return accountTokenRepository
                .findByTokenAndPurpose(hash(rawToken.trim()), PURPOSE_ACCOUNT_UNLOCK)
                .filter(token -> !token.isUsed())
                .filter(token -> token.getExpirationAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_ACCOUNT_UNLOCK_TOKEN",
                        "El token de desbloqueo no es válido o expiró."
                ));
    }

    public CreatedEmailVerificationToken createPasswordRecoveryToken(Account account) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(appProperties.passwordRecovery().expiresIn());

        accountTokenRepository.save(tokenMapper.toAccountToken(
                account,
                PURPOSE_PASSWORD_RECOVERY,
                hash(rawToken),
                expiresAt
        ));

        return new CreatedEmailVerificationToken(rawToken, expiresAt);
    }

    public AccountToken findValidPasswordRecoveryToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("INVALID_PASSWORD_RECOVERY_TOKEN", "El token de recuperación es obligatorio.");
        }

        AccountToken accountToken = accountTokenRepository
            .lockByTokenAndPurpose(hash(rawToken.trim()), PURPOSE_PASSWORD_RECOVERY)
                .orElseThrow(() -> new BadRequestException(
                        "INVALID_PASSWORD_RECOVERY_TOKEN",
                        "El token de recuperación no es válido."
                ));

        if (accountToken.isUsed()) {
            throw new BadRequestException(
                    "PASSWORD_RECOVERY_TOKEN_USED",
                    "El token de recuperación ya fue utilizado."
            );
        }

        if (!accountToken.getExpirationAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException(
                    "PASSWORD_RECOVERY_TOKEN_EXPIRED",
                    "El token de recuperación expiró."
            );
        }

        return accountToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CreatedEmailVerificationToken(
            String rawToken,
            LocalDateTime expiresAt
    ) {
    }

    public record IssuedVerification(
            String rawToken,
            LocalDateTime linkExpiresAt,
            String code,
            LocalDateTime codeExpiresAt
    ) {
    }
}
