package com.autopartco.backend.marketplace.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.core.settings.AccountSettings;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.config.AppProperties;

class QuestionNotificationTest {
    private final QuestionQueries queries = mock(QuestionQueries.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private final AppProperties appProperties = mock(AppProperties.class);
    private final QuestionNotifier notifier = new QuestionNotifier(queries, notifications, emailService,
            settingsQueries, appProperties);

    private final UUID questionId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID authorPerson = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID branchStaff = UUID.randomUUID();
    private final UUID authorAccount = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-11T15:00:00Z");

    @BeforeEach
    void frontend() {
        when(appProperties.frontendBaseUrl()).thenReturn("https://autopartco.co/");
    }

    private QuestionQueries.QuestionRow row(String answer, OffsetDateTime hiddenAt) {
        return new QuestionQueries.QuestionRow(questionId, productId, "Filtro de aceite", storeId, "Repuestos A",
                authorPerson, "¿Sirve para un Mazda 3 2015?", answer, answer == null ? null : now, now, hiddenAt,
                null, "Ana", "Pérez");
    }

    private StoreSettings settings(boolean questions, List<String> recipients) {
        return new StoreSettings(new StoreSettings.Contact(true, "{enlace}"),
                new StoreSettings.Profile(true, List.of(), null), new StoreSettings.Catalog(false),
                new StoreSettings.Notifications(true, true, true, true, questions, recipients),
                new StoreSettings.Security(false));
    }

    private AccountSettings account(boolean storeReplies) {
        return new AccountSettings(new AccountSettings.Security(new AccountSettings.TwoFactor(false, "EMAIL"), true),
                new AccountSettings.Notifications(true, true, storeReplies, false),
                new AccountSettings.Preferences("system", null));
    }

    // ------------------------------------------------------------ pregunta nueva

    @Test
    void newQuestionNotifiesOwnerAndBranchStaffWithALinkToThePortal() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)));
        when(queries.storeRecipients(storeId, productId, authorPerson)).thenReturn(List.of(owner, branchStaff));
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, List.of("ventas@almacen.co")));

        notifier.asked(questionId);

        String path = "/portal/" + storeId + "/preguntas?pregunta=" + questionId;
        verify(notifications).notify(owner, "Nueva pregunta sobre Filtro de aceite", "¿Sirve para un Mazda 3 2015?",
                path);
        verify(notifications).notify(branchStaff, "Nueva pregunta sobre Filtro de aceite",
                "¿Sirve para un Mazda 3 2015?", path);
        verify(emailService).sendQuestionAsked("ventas@almacen.co", "Repuestos A", "Filtro de aceite",
                "¿Sirve para un Mazda 3 2015?", "https://autopartco.co" + path);
    }

    @Test
    void withoutConfiguredRecipientsTheStoreEmailIsUsed() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)));
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, List.of()));
        when(queries.storeEmail(storeId)).thenReturn(Optional.of("Contacto@Almacen.co"));

        notifier.asked(questionId);

        verify(emailService).sendQuestionAsked(eq("contacto@almacen.co"), any(), any(), any(), any());
    }

    @Test
    void disabledQuestionsSettingSkipsTheEmailButKeepsInAppNotifications() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)));
        when(queries.storeRecipients(storeId, productId, authorPerson)).thenReturn(List.of(owner));
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(false, List.of("ventas@almacen.co")));

        notifier.asked(questionId);

        verify(notifications).notify(eq(owner), anyString(), anyString(), anyString());
        verifyNoInteractions(emailService);
    }

    @Test
    void oneFailingRecipientDoesNotStopTheOthers() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)));
        when(queries.storeRecipients(storeId, productId, authorPerson)).thenReturn(List.of(owner, branchStaff));
        doThrow(new IllegalStateException("db")).when(notifications)
                .notify(eq(owner), anyString(), anyString(), anyString());
        when(settingsQueries.storeSettings(storeId)).thenReturn(settings(true, List.of("a@x.co", "b@x.co")));
        doThrow(new IllegalStateException("smtp")).when(emailService)
                .sendQuestionAsked(eq("a@x.co"), any(), any(), any(), any());

        notifier.asked(questionId);

        verify(notifications).notify(eq(branchStaff), anyString(), anyString(), anyString());
        verify(emailService).sendQuestionAsked(eq("b@x.co"), any(), any(), any(), any());
    }

    @Test
    void hiddenOrDeletedQuestionsDoNotNotify() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, now)));
        notifier.asked(questionId);
        when(queries.find(questionId)).thenReturn(Optional.empty());
        notifier.asked(questionId);
        notifier.answered(questionId);

        verifyNoInteractions(notifications, emailService, settingsQueries);
    }

    @Test
    void recipientsAreTheOwnerAndActiveStaffOfBranchesWithTheProductWithoutDuplicates() {
        assertThat(QuestionQueries.STORE_RECIPIENTS_SQL)
                .contains("SELECT DISTINCT a.id", "st.code = 'ACTIVE'", "r.code = 'OWNER'", "branches_collaborators",
                        "i.product_id = :productId", "b.store_id = :storeId", "c.person_id <> :authorPersonId",
                        "a.deleted_at IS NULL");
    }

    // ------------------------------------------------------------ respuesta

    @Test
    void answerNotifiesTheAuthorInAppAndByEmail() {
        when(queries.find(questionId)).thenReturn(Optional.of(row("Sí, sirve.", null)));
        when(queries.author(authorPerson)).thenReturn(Optional.of(
                new QuestionQueries.Author(authorAccount, "ana@correo.co", "Ana María")));
        when(settingsQueries.accountSettings(authorAccount)).thenReturn(account(true));

        notifier.answered(questionId);

        String path = "/producto/" + productId + "?pregunta=" + questionId + "#preguntas";
        verify(notifications).notify(authorAccount, "Respondieron tu pregunta",
                "Repuestos A respondió tu pregunta sobre Filtro de aceite.", path);
        verify(emailService).sendQuestionAnswered("ana@correo.co", "Ana", "Repuestos A", "Filtro de aceite",
                "¿Sirve para un Mazda 3 2015?", "Sí, sirve.", "https://autopartco.co" + path);
    }

    @Test
    void answeringAgainAfterRemovingTheAnswerDoesNotNotifyTwice() {
        when(queries.find(questionId)).thenReturn(Optional.of(row("Sí, sirve.", null)));
        when(queries.author(authorPerson)).thenReturn(Optional.of(
                new QuestionQueries.Author(authorAccount, "ana@correo.co", "Ana María")));
        when(settingsQueries.accountSettings(authorAccount)).thenReturn(account(true));
        String path = "/producto/" + productId + "?pregunta=" + questionId + "#preguntas";
        // La primera respuesta deja la notificación guardada; la segunda la encuentra.
        when(notifications.exists(authorAccount, path)).thenReturn(false, true);

        notifier.answered(questionId);
        notifier.answered(questionId);

        verify(notifications).notify(eq(authorAccount), anyString(), anyString(), eq(path));
        verify(emailService).sendQuestionAnswered(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void authorWhoTurnedOffStoreRepliesOnlyGetsTheInAppNotification() {
        when(queries.find(questionId)).thenReturn(Optional.of(row("Sí", null)));
        when(queries.author(authorPerson)).thenReturn(Optional.of(
                new QuestionQueries.Author(authorAccount, "ana@correo.co", "Ana")));
        when(settingsQueries.accountSettings(authorAccount)).thenReturn(account(false));

        notifier.answered(questionId);

        verify(notifications).notify(eq(authorAccount), anyString(), anyString(), anyString());
        verify(emailService, never()).sendQuestionAnswered(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listenersRunAfterCommitInTheBackground() throws NoSuchMethodException {
        for (Class<?> event : List.of(ProductQuestionAsked.class, ProductQuestionAnswered.class)) {
            Method method = QuestionNotificationListener.class.getDeclaredMethod("on", event);
            assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(method.getAnnotation(Async.class).value())
                    .isEqualTo(QuestionAsyncConfig.QUESTION_NOTIFICATION_EXECUTOR);
        }
    }

    @Test
    void listenerSwallowsNotifierFailures() {
        QuestionNotifier failing = mock(QuestionNotifier.class);
        doThrow(new IllegalStateException("boom")).when(failing).asked(questionId);
        QuestionNotificationListener listener = new QuestionNotificationListener(failing);

        listener.on(new ProductQuestionAsked(questionId));

        verify(failing).asked(questionId);
    }
}
