package com.autopartco.backend.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Enlace de las notificaciones in-app y plantillas de correo de preguntas. */
class NotificationLinkTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NotificationService service = new NotificationService(jdbcTemplate);
    private final UUID accountId = UUID.randomUUID();

    @Test
    void internalLinkIsStored() {
        service.notify(accountId, "Título", "Mensaje", "/portal/abc/preguntas?pregunta=1");

        verify(jdbcTemplate).update("INSERT INTO notifications (account_id, title, message, link) VALUES (?, ?, ?, ?)",
                accountId, "Título", "Mensaje", "/portal/abc/preguntas?pregunta=1");
    }

    @Test
    void notificationsWithoutLinkKeepWorking() {
        service.notify(accountId, "Pedido actualizado", "Mensaje");

        verify(jdbcTemplate).update("INSERT INTO notifications (account_id, title, message, link) VALUES (?, ?, ?, ?)",
                accountId, "Pedido actualizado", "Mensaje", null);
    }

    @Test
    void externalOrProtocolRelativeLinksAreRejected() {
        for (String link : new String[] {"https://evil.co", "//evil.co/x", "portal/x", "/" + "x".repeat(300)}) {
            assertThatThrownBy(() -> service.notify(accountId, "T", "M", link))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void longTitlesAreCutToTheColumnSize() {
        service.notify(accountId, "x".repeat(200), "Mensaje", null);

        verify(jdbcTemplate).update("INSERT INTO notifications (account_id, title, message, link) VALUES (?, ?, ?, ?)",
                accountId, "x".repeat(149) + "…", "Mensaje", null);
    }

    @Test
    void questionEmailsEscapeUserText() {
        String asked = EmailTemplate.questionAsked("Repuestos & Cía", "Filtro <b>", "<script>x</script>",
                "https://autopartco.co/portal/s/preguntas?pregunta=q&x=1");
        assertThat(asked).contains("Responder en el portal").contains("pregunta=q&amp;x=1")
                .doesNotContain("<script>").contains("&lt;script&gt;").doesNotContain("Filtro <b>");

        String answered = EmailTemplate.questionAnswered("<i>Ana</i>", "Repuestos A", "Filtro", "¿Sirve?",
                "<a href=x>Sí</a>", "https://autopartco.co/producto/p#preguntas");
        assertThat(answered).contains("Ver la respuesta").contains("#preguntas").doesNotContain("<i>Ana</i>")
                .doesNotContain("<a href=x>");
    }
}
