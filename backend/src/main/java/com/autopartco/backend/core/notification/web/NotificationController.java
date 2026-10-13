package com.autopartco.backend.core.notification.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.notification.NotificationResponse;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/me/notifications")
@AllArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

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
        // Hasta la fase 2 (core.identity) no hay fachada de cuenta actual.
        return jdbcTemplate.queryForList(
                "SELECT id FROM accounts WHERE lower(email) = lower(?)",
                UUID.class,
                authentication.getName()).stream().findFirst();
    }
}
