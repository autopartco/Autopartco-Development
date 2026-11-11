package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.web.ResendVerificationRequest;
import com.autopartco.backend.core.identity.web.ResendVerificationResponse;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.persistence.StateCodes;

import lombok.AllArgsConstructor;

/** Reenvío del correo de verificación. Responde igual exista o no la cuenta para no revelar su estado. */
@Service
@AllArgsConstructor
public class ResendVerificationService {
    static final int COOLDOWN_SECONDS = 60;
    static final int MAX_PER_HOUR = 5;
    static final String MESSAGE = "Si la cuenta existe y no está verificada, te enviamos un correo.";

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailVerificationTokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final AppProperties appProperties;

    @Transactional
    public ResendVerificationResponse resend(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        accountRepository.lockByEmail(email)
                .filter(account -> account.getState() != null
                        && StateCodes.PENDING.equals(account.getState().getCode()))
                .ifPresent(this::resendIfAllowed);
        return new ResendVerificationResponse(MESSAGE, COOLDOWN_SECONDS);
    }

    private void resendIfAllowed(Account account) {
        LocalDateTime now = LocalDateTime.now();
        String purpose = EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION;

        boolean coolingDown = accountTokenRepository
                .findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(account.getId(), purpose)
                .map(AccountToken::getCreatedAt)
                .filter(createdAt -> createdAt.isAfter(now.minusSeconds(COOLDOWN_SECONDS)))
                .isPresent();
        if (coolingDown) {
            return;
        }
        long lastHour = accountTokenRepository
                .countByAccount_IdAndPurposeAndCreatedAtAfter(account.getId(), purpose, now.minusHours(1));
        if (lastHour >= MAX_PER_HOUR) {
            return;
        }

        tokenService.invalidatePendingVerification(account.getId());
        EmailVerificationTokenService.IssuedVerification issued = tokenService.issueVerification(account);
        eventPublisher.publishEvent(new EmailVerificationRequested(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                tokenService.verificationLink(issued.rawToken()),
                issued.code(),
                appProperties.emailVerification().expiresIn().toMinutes(),
                tokenService.codeValidity().toMinutes()
        ));
        auditService.record(account.getId(), "EMAIL_VERIFICATION_RESENT", "ACCOUNT", account.getId(), Map.of());
    }
}
