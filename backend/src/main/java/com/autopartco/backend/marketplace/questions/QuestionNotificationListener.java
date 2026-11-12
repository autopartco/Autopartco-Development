package com.autopartco.backend.marketplace.questions;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Avisa de preguntas y respuestas solo si se confirmaron; un fallo del aviso no revierte nada. */
@Component
@AllArgsConstructor
@Slf4j
class QuestionNotificationListener {
    private final QuestionNotifier notifier;

    @Async(QuestionAsyncConfig.QUESTION_NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProductQuestionAsked event) {
        try {
            notifier.asked(event.questionId());
        } catch (RuntimeException exception) {
            log.error("Could not notify new product question ({})", exception.getClass().getSimpleName());
        }
    }

    @Async(QuestionAsyncConfig.QUESTION_NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProductQuestionAnswered event) {
        try {
            notifier.answered(event.questionId());
        } catch (RuntimeException exception) {
            log.error("Could not notify product question answer ({})", exception.getClass().getSimpleName());
        }
    }
}
