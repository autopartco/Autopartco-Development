package com.autopartco.backend.core.notification;

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
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
                        + EmailTemplate.paragraph("<strong>Fecha:</strong> " + EmailTemplate.escape(when)
                                + " (hora de Colombia)<br><strong>Dispositivo:</strong> " + EmailTemplate.escape(device)
                                + "<br><strong>Dirección IP aproximada:</strong> " + EmailTemplate.escape(maskedIp))
                        + EmailTemplate.note("Si fuiste tú, no tienes que hacer nada. Si no lo reconoces, cambia tu "
                                + "contraseña y cierra las demás sesiones desde tu cuenta."));

        sendHtml(to, "Nuevo inicio de sesión en Autopartco", text, html);
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject("Desbloquea tu cuenta de Autopartco");
        message.setText("""
                Hola %s,

                Tu cuenta fue bloqueada por varios intentos de inicio de sesión fallidos.
                Para desbloquearla, abre este enlace:
                %s

                Si no solicitaste este desbloqueo, puedes ignorar este correo.
                """.formatted(fullName, unlockLink));

        mailSender.send(message);
    }

    public void sendPasswordRecovery(String to, String fullName, String token, LocalDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject("Recupera tu contraseña de Autopartco");
        message.setText("""
                Hola %s,

                Recibimos una solicitud para cambiar la contraseña de tu cuenta.
                Usa este token en el formulario de recuperación:
                %s

                El token vence el %s y solo puede utilizarse una vez.
                Si no solicitaste este cambio, puedes ignorar este correo.
                """.formatted(fullName, token, expiresAt));

        mailSender.send(message);
    }

    public void sendSellerOnboarding(String to, String fullName, String setupLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject("Tu solicitud de vendedor fue aprobada");
        message.setText("""
                Hola %s,

                Revisamos y aprobamos tu solicitud para vender en Autopartco.
                Para terminar de configurar tu almacén, abre este enlace e inicia sesión
                con la cuenta con la que hiciste la solicitud:
                %s

                Por seguridad, solo esa cuenta puede completar la configuración.
                """.formatted(fullName, setupLink));

        mailSender.send(message);
    }

    /**
     * Aviso de alertas nuevas de un almacén (un solo correo por destinatario). {@code alertLines} son líneas ya
     * redactadas en español; {@code portalLink} lleva al centro de alertas del portal.
     */
    public void sendStoreAlerts(String to, String storeName, java.util.List<String> alertLines, String portalLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject(alertLines.size() == 1
                ? "Nueva alerta en " + storeName
                : alertLines.size() + " alertas nuevas en " + storeName);
        message.setText("""
                Hola,

                Autopartco detectó %s sobre tu almacén %s:

                %s

                Revísalas y gestiónalas en el portal:
                %s

                Recibes este correo porque tienes activas las alertas por correo. Puedes cambiarlo en
                Alertas > Preferencias.
                """.formatted(alertLines.size() == 1 ? "una novedad" : "novedades", storeName,
                String.join("\n", alertLines.stream().map(line -> "- " + line).toList()), portalLink));

        mailSender.send(message);
    }

    public void sendSellerCollaboratorInvitation(
            String to,
            String storeName,
            String roleName,
            String invitationLink,
            LocalDateTime expiresAt
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject("Invitación para colaborar en " + storeName);
        message.setText("""
                Recibiste una invitación para colaborar en el almacén %s con el rol "%s".

                Para aceptar y activar tu acceso, abre este enlace:
                %s

                El enlace es de un solo uso y vence el %s. Si ya venció, solicita al
                administrador del almacén que reenvíe la invitación.
                """.formatted(storeName, roleName, invitationLink, expiresAt));

        mailSender.send(message);
    }
}
