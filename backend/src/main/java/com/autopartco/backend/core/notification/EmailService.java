package com.autopartco.backend.core.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.autopartco.backend.shared.config.AppProperties;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public void sendEmailVerification(String to, String fullName, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.emailVerification().from());
        message.setTo(to);
        message.setSubject("Verifica tu correo en Autopartco");
        message.setText("""
                Hola %s,

                Recibimos una solicitud para crear tu cuenta en Autopartco.

                Para activar tu cuenta, abre este enlace:
                %s

                Si no solicitaste esta cuenta, puedes ignorar este correo.
                """.formatted(fullName, verificationLink));

        mailSender.send(message);
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
                Para terminar de configurar tu almacén, abre este enlace:
                %s

                El enlace es personal y de un solo uso.
                """.formatted(fullName, setupLink));

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
