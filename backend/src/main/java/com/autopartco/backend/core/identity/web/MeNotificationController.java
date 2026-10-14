package com.autopartco.backend.core.identity.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.notification.NotificationResponse;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/me/notifications")
@AllArgsConstructor
/** Notificaciones in-app del usuario autenticado. Vive en identity porque /me es de identity y notification no conoce cuentas (§4.2). */
public class MeNotificationController {

    private final NotificationService notificationService;
    private final AccountLookup accountLookup;

    @GetMapping
    public List<NotificationResponse> list(Authentication authentication) {
        return accountId(authentication).map(notificationService::listFor).orElseGet(List::of);
    }

    @PatchMapping("/{notificationId}/read")
    public void markRead(@PathVariable UUID notificationId, Authentication authentication) {
        UUID accountId = accountId(authentication).orElseThrow(() ->
                new NotFoundException("NOTIFICATION_NOT_FOUND", "No se encontró la notificación."));
        notificationService.markRead(accountId, notificationId);
    }

    private Optional<UUID> accountId(Authentication authentication) {
        return accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId);
    }
}
