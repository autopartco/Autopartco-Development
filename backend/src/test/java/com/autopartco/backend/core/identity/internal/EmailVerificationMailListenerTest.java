package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import com.autopartco.backend.core.notification.EmailService;

class EmailVerificationMailListenerTest {
    @Test
    void smtpFailureIsSwallowedSoTheRegistrationIsNotRolledBack() {
        EmailService emailService = mock(EmailService.class);
        doThrow(new MailSendException("smtp down for ana@example.com code 123456")).when(emailService)
                .sendEmailVerification(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong());
        EmailVerificationMailListener listener = new EmailVerificationMailListener(emailService);

        assertDoesNotThrow(() -> listener.on(new EmailVerificationRequested(
                "ana@example.com", "Ana", "https://app/verify-email?token=t", "123456", 60, 30)));

        verify(emailService).sendEmailVerification("ana@example.com", "Ana",
                "https://app/verify-email?token=t", "123456", 60, 30);
    }
}
