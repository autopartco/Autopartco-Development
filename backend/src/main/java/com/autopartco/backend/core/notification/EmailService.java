package com.autopartco.backend.core.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.autopartco.backend.shared.config.AppProperties;
import java.time.LocalDateTime;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmailService {
    /** Fechas de vencimiento legibles: «12 de noviembre de 2026, 3:40 p. m.» (hora de Colombia). */
    static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", Locale.forLanguageTag("es-CO"));

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    /** Correo HTML (con alternativa de texto) con el botón de verificación y el código de 6 dígitos. */
    public void sendEmailVerification(
            String to,
            String fullName,
            String verificationLink,
            String code,
            long linkValidMinutes,
            long codeValidMinutes
    ) {
        String text = """
                Hola %s,

                Recibimos una solicitud para crear tu cuenta en Autopartco.

                Para activarla, abre este enlace (vence en %s):
                %s

                O ingresa este código de verificación (vence en %s):
                %s

                Si no solicitaste esta cuenta, puedes ignorar este correo.
                """.formatted(fullName, formatMinutes(linkValidMinutes), verificationLink,
                formatMinutes(codeValidMinutes), code);

        String html = EmailTemplate.layout("Verifica tu correo",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Recibimos una solicitud para crear tu cuenta en Autopartco. "
                                + "Confirma tu correo para activarla.")
                        + EmailTemplate.button("Verificar mi correo", verificationLink)
                        + EmailTemplate.note("El enlace vence en " + formatMinutes(linkValidMinutes) + ".")
                        + EmailTemplate.paragraph("¿Prefieres ingresar un código? Escríbelo en la pantalla de verificación:")
                        + EmailTemplate.highlightedCode(code)
                        + EmailTemplate.note("El código vence en " + formatMinutes(codeValidMinutes) + ".")
                        + EmailTemplate.note("Si no solicitaste esta cuenta, puedes ignorar este correo."));

        sendHtml(to, "Verifica tu correo en Autopartco", text, html);
    }

    /**
     * Código de verificación en dos pasos. {@code purposeText} describe la acción en minúsculas
     * (p. ej. «iniciar sesión», «activar la verificación en dos pasos»).
     */
    public void sendTwoFactorCode(String to, String fullName, String code, String purposeText, long validMinutes) {
        String text = """
                Hola %s,

                Usa este código para %s en Autopartco (vence en %s):
                %s

                Si no fuiste tú, cambia tu contraseña: alguien podría conocerla.
                """.formatted(fullName, purposeText, formatMinutes(validMinutes), code);

        String html = EmailTemplate.layout("Tu código de verificación",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Usa este código para " + EmailTemplate.escape(purposeText)
                                + " en Autopartco:")
                        + EmailTemplate.highlightedCode(code)
                        + EmailTemplate.note("El código vence en " + formatMinutes(validMinutes)
                                + " y solo puede usarse una vez.")
                        + EmailTemplate.note("Si no fuiste tú, cambia tu contraseña: alguien podría conocerla."));

        sendHtml(to, "Tu código de verificación de Autopartco", text, html);
    }

    /** Aviso de inicio de sesión desde un dispositivo nuevo. Los datos llegan ya resumidos (IP enmascarada). */
    /** Aviso de seguridad de la cuenta (contraseña o correo cambiados). {@code message} es texto plano. */
    public void sendSecurityNotice(String to, String fullName, String title, String message) {
        String text = """
                Hola %s,

                %s

                Si no fuiste tú, recupera tu contraseña de inmediato desde "¿Olvidaste tu contraseña?" y escríbenos.
                """.formatted(fullName, message);
        String html = EmailTemplate.layout(title,
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph(EmailTemplate.escape(message))
                        + EmailTemplate.note("Si no fuiste tú, recupera tu contraseña de inmediato desde "
                                + "«¿Olvidaste tu contraseña?» y escríbenos."));
        sendHtml(to, title + " · Autopartco", text, html);
    }

    public void sendLoginAlert(String to, String fullName, String when, String device, String maskedIp) {
        String text = """
                Hola %s,

                Detectamos un nuevo inicio de sesión en tu cuenta de Autopartco.

                Fecha: %s (hora de Colombia)
                Dispositivo: %s
                Dirección IP aproximada: %s

                Si fuiste tú, no tienes que hacer nada. Si no lo reconoces, cambia tu contraseña y cierra las
                demás sesiones desde tu cuenta.
                """.formatted(fullName, when, device, maskedIp);

        String html = EmailTemplate.layout("Nuevo inicio de sesión",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Detectamos un nuevo inicio de sesión en tu cuenta de Autopartco.")
                        + EmailTemplate.detail("Fecha", when + " (hora de Colombia)", true)
                        + EmailTemplate.detail("Dispositivo", device, false)
                        + EmailTemplate.detail("Dirección IP aproximada", maskedIp, true)
                        + EmailTemplate.note("Si fuiste tú, no tienes que hacer nada. Si no lo reconoces, cambia tu "
                                + "contraseña y cierra las demás sesiones desde tu cuenta."));

        sendHtml(to, "Nuevo inicio de sesión en Autopartco", text, html);
    }

    /** Recordatorio de calificación (como máximo dos por venta; ver {@code ReviewReminderJob}). */
    public void sendReviewReminder(String to, String givenName, long orderNumber, String reviewLink, String settingsLink) {
        String text = """
                Hola %s,

                Hace unos días recibiste tu pedido #%d. Si ya usaste los repuestos, cuéntanos cómo te fue:
                %s

                Es opcional y no te lo recordaremos más de dos veces. Puedes desactivar estos recordatorios en %s
                """.formatted(givenName, orderNumber, reviewLink, settingsLink);

        sendHtml(to, "Califica tu pedido #" + orderNumber, text,
                EmailTemplate.reviewReminder(givenName, orderNumber, reviewLink, settingsLink));
    }

    /** Invitación a calificar un pedido entregado; {@code reviewLink} lleva a {@code /cuenta/calificar?pedido=…}. */
    public void sendReviewInvitation(String to, String givenName, long orderNumber, String reviewLink) {
        String text = """
                Hola %s,

                Tu pedido #%d fue entregado. Cuéntanos qué tal estuvo el almacén y los repuestos que recibiste:
                %s

                Calificar toma un minuto y es opcional. Si no quieres hacerlo, puedes ignorar este correo.
                """.formatted(givenName, orderNumber, reviewLink);

        sendHtml(to, "¿Cómo te fue con tu pedido?", text,
                EmailTemplate.reviewInvitation(givenName, orderNumber, reviewLink));
    }

    /** Aviso al almacén de una pregunta nueva; {@code portalLink} lleva a la pregunta en el portal. */
    public void sendQuestionAsked(String to, String storeName, String productName, String question,
            String portalLink) {
        String text = """
                Hola,

                Un comprador preguntó por %s en %s:

                "%s"

                Respóndela en el portal:
                %s

                Recibes este correo porque tu almacén tiene activos los avisos de preguntas. Puedes cambiarlo en
                Configuración > Notificaciones.
                """.formatted(productName, storeName, question, portalLink);

        sendHtml(to, "Nueva pregunta sobre " + oneLine(productName), text,
                EmailTemplate.questionAsked(storeName, productName, question, portalLink));
    }

    /** Aviso al comprador de que el almacén respondió su pregunta; {@code productLink} lleva a la ficha. */
    public void sendQuestionAnswered(String to, String givenName, String storeName, String productName,
            String question, String answer, String productLink) {
        String text = """
                Hola %s,

                %s respondió tu pregunta sobre %s.

                Tu pregunta: "%s"
                Respuesta: "%s"

                Míralo en la ficha del producto:
                %s
                """.formatted(givenName, storeName, productName, question, answer, productLink);

        sendHtml(to, "Respondieron tu pregunta sobre " + oneLine(productName), text,
                EmailTemplate.questionAnswered(givenName, storeName, productName, question, answer, productLink));
    }

    /** Asunto en una línea (sin saltos que alteren las cabeceras). */
    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").strip();
    }

    private void sendHtml(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.emailVerification().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new MailPreparationException(exception);
        }
    }

    private static String formatMinutes(long minutes) {
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hora" : " horas");
        }
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }

    public void sendAccountUnlock(String to, String fullName, String unlockLink) {
        String text = """
                Hola %s,

                Tu cuenta fue bloqueada por varios intentos de inicio de sesión fallidos.
                Para desbloquearla, abre este enlace:
                %s

                Si no solicitaste este desbloqueo, puedes ignorar este correo.
                """.formatted(fullName, unlockLink);
        String html = EmailTemplate.layout("Desbloquea tu cuenta",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Bloqueamos tu cuenta por varios intentos de inicio de sesión fallidos. "
                                + "Para desbloquearla, usa este botón:")
                        + EmailTemplate.button("Desbloquear mi cuenta", unlockLink)
                        + EmailTemplate.note("Si no fuiste tú, ignora este correo y considera cambiar tu contraseña."));
        sendHtml(to, "Desbloquea tu cuenta de Autopartco", text, html);
    }

    public void sendPasswordRecovery(String to, String fullName, String token, LocalDateTime expiresAt) {
        String expires = EXPIRY.format(expiresAt);
        String text = """
                Hola %s,

                Recibimos una solicitud para cambiar la contraseña de tu cuenta.
                Usa este código en el formulario de recuperación:
                %s

                Vence el %s y solo puede utilizarse una vez.
                Si no solicitaste este cambio, puedes ignorar este correo.
                """.formatted(fullName, token, expires);
        String html = EmailTemplate.layout("Recupera tu contraseña",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Recibimos una solicitud para cambiar la contraseña de tu cuenta. "
                                + "Copia este código en el formulario de recuperación:")
                        + EmailTemplate.highlightedCode(token)
                        + EmailTemplate.note("Vence el " + EmailTemplate.escape(expires) + " y solo sirve una vez. "
                                + "Si no pediste el cambio, ignora este correo: tu contraseña sigue igual."));
        sendHtml(to, "Recupera tu contraseña de Autopartco", text, html);
    }

    public void sendSellerOnboarding(String to, String fullName, String setupLink) {
        String text = """
                Hola %s,

                Revisamos y aprobamos tu solicitud para vender en Autopartco.
                Para terminar de configurar tu almacén, abre este enlace e inicia sesión
                con la cuenta con la que hiciste la solicitud:
                %s

                Por seguridad, solo esa cuenta puede completar la configuración.
                """.formatted(fullName, setupLink);
        String html = EmailTemplate.layout("Tu almacén fue aprobado",
                EmailTemplate.paragraph("Hola " + EmailTemplate.escape(fullName) + ",")
                        + EmailTemplate.paragraph("Revisamos y aprobamos tu solicitud para vender en Autopartco. "
                                + "Falta un paso: configura tu almacén para publicarlo.")
                        + EmailTemplate.button("Configurar mi almacén", setupLink)
                        + EmailTemplate.note("Ingresa con la cuenta con la que hiciste la solicitud: por seguridad, "
                                + "solo esa cuenta puede completar la configuración."));
        sendHtml(to, "Tu solicitud de vendedor fue aprobada", text, html);
    }

    /**
     * Aviso de alertas nuevas de un almacén (un solo correo por destinatario). {@code alertLines} son líneas ya
     * redactadas en español; {@code portalLink} lleva al centro de alertas del portal.
     */
    public void sendStoreAlerts(String to, String storeName, java.util.List<String> alertLines, String portalLink) {
        boolean single = alertLines.size() == 1;
        String subject = single
                ? "Nueva alerta en " + oneLine(storeName)
                : alertLines.size() + " alertas nuevas en " + oneLine(storeName);
        String text = """
                Hola,

                Autopartco detectó %s sobre tu almacén %s:

                %s

                Revísalas y gestiónalas en el portal:
                %s

                Recibes este correo porque tienes activas las alertas por correo. Puedes cambiarlo en
                Alertas > Preferencias.
                """.formatted(single ? "una novedad" : "novedades", storeName,
                String.join("\n", alertLines.stream().map(line -> "- " + line).toList()), portalLink);
        String html = EmailTemplate.layout(single ? "Nueva alerta en tu almacén" : alertLines.size() + " alertas nuevas",
                EmailTemplate.paragraph("Detectamos " + (single ? "una novedad" : "novedades") + " en <strong>"
                        + EmailTemplate.escape(storeName) + "</strong>:")
                        + EmailTemplate.bulletList(alertLines)
                        + EmailTemplate.button("Ver alertas en el portal", portalLink)
                        + EmailTemplate.note("Recibes este correo porque tienes activas las alertas por correo. "
                                + "Puedes cambiarlo en Alertas &gt; Preferencias."));
        sendHtml(to, subject, text, html);
    }

    public void sendSellerCollaboratorInvitation(
            String to,
            String storeName,
            String roleName,
            String invitationLink,
            LocalDateTime expiresAt
    ) {
        String expires = EXPIRY.format(expiresAt);
        String text = """
                Recibiste una invitación para colaborar en el almacén %s con el rol "%s".

                Para aceptar y activar tu acceso, abre este enlace:
                %s

                El enlace es de un solo uso y vence el %s. Si ya venció, solicita al
                administrador del almacén que reenvíe la invitación.
                """.formatted(storeName, roleName, invitationLink, expires);
        String html = EmailTemplate.layout("Te invitaron a un equipo",
                EmailTemplate.paragraph("<strong>" + EmailTemplate.escape(storeName) + "</strong> te invitó a su equipo en "
                        + "Autopartco.")
                        + EmailTemplate.detail("Rol", roleName, false)
                        + EmailTemplate.detail("Vence", expires, true)
                        + EmailTemplate.button("Aceptar invitación", invitationLink)
                        + EmailTemplate.note("El enlace es de un solo uso. Si ya venció, pide al administrador del almacén "
                                + "que reenvíe la invitación. Si no esperabas este correo, ignóralo."));
        sendHtml(to, "Invitación para colaborar en " + oneLine(storeName), text, html);
    }
}
