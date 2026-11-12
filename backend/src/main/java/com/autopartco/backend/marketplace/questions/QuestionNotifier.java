package com.autopartco.backend.marketplace.questions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisos de preguntas. Pregunta nueva: notificación in-app (una por cuenta) al dueño del almacén y a los colaboradores
 * activos de las sucursales con inventario del producto, y correo a {@code notifications.recipients} (o, sin
 * destinatarios, al correo del almacén) si {@code notifications.questions} está activo. Respuesta: notificación y
 * correo al autor (el correo, si su ajuste {@code notifications.storeReplies} lo permite). Las preguntas ocultas o
 * borradas no avisan. Un fallo con un destinatario no corta a los demás; los registros no incluyen correos ni textos.
 */
@Service
@AllArgsConstructor
@Slf4j
public class QuestionNotifier {
    static final String ASKED_TITLE = "Nueva pregunta sobre ";
    static final String ANSWERED_TITLE = "Respondieron tu pregunta";

    private final QuestionQueries queries;
    private final NotificationService notifications;
    private final EmailService emailService;
    private final SettingsQueries settingsQueries;
    private final AppProperties appProperties;

    /** Ruta interna del portal que abre la pregunta. */
    static String portalPath(UUID storeId, UUID questionId) {
        return "/portal/" + storeId + "/preguntas?pregunta=" + questionId;
    }

    /** Ruta interna de la ficha del producto, en la sección de preguntas. */
    static String productPath(UUID productId) {
        return "/producto/" + productId + "#preguntas";
    }

    public void asked(UUID questionId) {
        Optional<QuestionQueries.QuestionRow> found = queries.find(questionId).filter(row -> !row.hidden());
        if (found.isEmpty()) {
            return;
        }
        QuestionQueries.QuestionRow row = found.get();
        String path = portalPath(row.storeId(), row.id());
        for (UUID accountId : queries.storeRecipients(row.storeId(), row.productId(), row.personId())) {
            try {
                notifications.notify(accountId, ASKED_TITLE + row.productName(), row.question(), path);
            } catch (RuntimeException failure) {
                log.warn("Could not store question notification ({})", failure.getClass().getSimpleName());
            }
        }
        StoreSettings.Notifications preferences = settingsQueries.storeSettings(row.storeId()).notifications();
        if (!preferences.questions()) {
            return;
        }
        String link = absolute(path);
        for (String recipient : emails(row.storeId(), preferences.recipients())) {
            try {
                emailService.sendQuestionAsked(recipient, row.storeName(), row.productName(), row.question(), link);
            } catch (RuntimeException failure) {
                log.warn("Could not email question notification ({})", failure.getClass().getSimpleName());
            }
        }
    }

    public void answered(UUID questionId) {
        Optional<QuestionQueries.QuestionRow> found = queries.find(questionId)
                .filter(row -> !row.hidden() && row.answered());
        if (found.isEmpty()) {
            return;
        }
        QuestionQueries.QuestionRow row = found.get();
        Optional<QuestionQueries.Author> author = queries.author(row.personId());
        if (author.isEmpty()) {
            return;
        }
        QuestionQueries.Author to = author.get();
        String path = productPath(row.productId());
        notifications.notify(to.accountId(), ANSWERED_TITLE,
                row.storeName() + " respondió tu pregunta sobre " + row.productName() + ".", path);
        if (!settingsQueries.accountSettings(to.accountId()).notifications().storeReplies()) {
            return;
        }
        emailService.sendQuestionAnswered(to.email(), firstName(to.name()), row.storeName(), row.productName(),
                row.question(), row.answer(), absolute(path));
    }

    /** Destinatarios configurados o, sin ninguno, el correo del almacén; en minúsculas y sin repetir. */
    private List<String> emails(UUID storeId, List<String> configured) {
        Set<String> result = new LinkedHashSet<>();
        configured.stream().filter(email -> email != null && !email.isBlank())
                .forEach(email -> result.add(email.strip().toLowerCase(Locale.ROOT)));
        if (result.isEmpty()) {
            queries.storeEmail(storeId).filter(email -> !email.isBlank())
                    .ifPresent(email -> result.add(email.strip().toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(result);
    }

    /** URL absoluta del frontend para la ruta interna (con su consulta y su ancla). */
    String absolute(String path) {
        return UriComponentsBuilder.fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", "") + path)
                .build()
                .toUriString();
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
