package com.autopartco.backend.core.identity.internal;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.notification.EmailService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envía códigos 2FA y avisos de inicio de sesión solo si la transacción se confirmó; un fallo SMTP no la revierte. */
@Component
@AllArgsConstructor
@Slf4j
class SecurityMailListener {
    private final EmailService emailService;

    @Async(IdentityAsyncConfig.VERIFICATION_MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TwoFactorCodeRequested event) {
        try {
            emailService.sendTwoFactorCode(event.email(), event.fullName(), event.code(), event.purposeText(),
                    event.validMinutes());
        } catch (RuntimeException exception) {
            // Sin el objeto de la excepción: el mensaje del transporte puede incluir destinatario o código.
            log.error("Could not send two-factor code ({})", exception.getClass().getSimpleName());
        }
    }

    @Async(IdentityAsyncConfig.VERIFICATION_MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LoginAlertRequested event) {
        try {
            emailService.sendLoginAlert(event.email(), event.fullName(), event.when(), event.device(), event.maskedIp());
        } catch (RuntimeException exception) {
            log.error("Could not send login alert ({})", exception.getClass().getSimpleName());
        }
    }
}
