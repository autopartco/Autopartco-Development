package com.autopartco.backend.storesuite.onboarding;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.notification.SellerSetupMailer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envía el enlace de configuración solo si la transacción que emitió el token se confirmó. */
@Component
@AllArgsConstructor
@Slf4j
class SetupLinkMailListener {
    private final SellerSetupMailer sellerSetupMailer;

    @Async(OnboardingAsyncConfig.SETUP_MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(SetupLinkIssued event) {
        try {
            sellerSetupMailer.send(event.applicantEmail(), event.applicantGivenName(), event.onboardingToken());
        } catch (RuntimeException exception) {
            // Sin el objeto de la excepción: el mensaje del transporte puede incluir destinatario o enlace.
            log.error("Could not send seller setup link ({})", exception.getClass().getSimpleName());
        }
    }
}
