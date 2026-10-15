package com.autopartco.backend.storesuite.staff;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;

/** Envía el correo de invitación con el enlace {@code /seller/invitation?token=...} del frontend. */
@Component
@AllArgsConstructor
class StaffInvitationMailer {
    private final EmailService emailService;
    private final AppProperties appProperties;

    void send(StaffInvitations.Issued issued) {
        String link = UriComponentsBuilder
                .fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", ""))
                .path("/seller/invitation")
                .queryParam("token", issued.rawToken())
                .build()
                .toUriString();
        emailService.sendSellerCollaboratorInvitation(
                issued.invitation().email(),
                issued.invitation().storeName(),
                issued.invitation().roleName(),
                link,
                issued.invitation().expiresAt()
        );
    }
}
