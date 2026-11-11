package com.autopartco.backend.core.identity.internal;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.notification.EmailService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envía el correo de verificación solo si la transacción que lo pidió se confirmó; un fallo SMTP no la revierte. */
@Component
@AllArgsConstructor
@Slf4j
class EmailVerificationMailListener {
    private final EmailService emailService;

    @Async(IdentityAsyncConfig.VERIFICATION_MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(EmailVerificationRequested event) {
        try {
            emailService.sendEmailVerification(
                    event.email(),
                    event.fullName(),
                    event.verificationLink(),
                    event.code(),
                    event.linkValidMinutes(),
                    event.codeValidMinutes()
            );
        } catch (RuntimeException exception) {
            // Sin el objeto de la excepción: el mensaje del transporte puede incluir destinatario, enlace o código.
            log.error("Could not send email verification ({})", exception.getClass().getSimpleName());
        }
    }
}
