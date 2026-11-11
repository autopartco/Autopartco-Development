package com.autopartco.backend.marketplace.reviews;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.shared.events.SaleDelivered;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Envía la invitación a calificar solo si la entrega se confirmó; un fallo de correo no revierte nada. */
@Component
@AllArgsConstructor
@Slf4j
class ReviewInvitationListener {
    private final ReviewInvitationService invitations;

    @Async(ReviewMailAsyncConfig.REVIEW_MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(SaleDelivered event) {
        try {
            invitations.invite(event.orderId());
        } catch (RuntimeException exception) {
            // Sin el objeto de la excepción: el mensaje del transporte puede incluir destinatario o enlace.
            log.error("Could not send review invitation ({})", exception.getClass().getSimpleName());
        }
    }
}
