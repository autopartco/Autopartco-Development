package com.autopartco.backend.core.identity.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.notification.NotificationResponse;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Bandeja in-app del usuario autenticado. Vive en identity porque /me es de identity y notification no conoce
 * cuentas (§4.2). Todas las operaciones se limitan a las notificaciones de la propia cuenta.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/me/notifications")
@AllArgsConstructor
public class MeNotificationController {
    private final NotificationService notificationService;
    private final AccountLookup accountLookup;

    /** {@code status}: {@code active} (bandeja) o {@code archived} (histórico). Páginas desde 1. */
    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        boolean archived = switch (status) {
            case "active" -> false;
            case "archived" -> true;
            default -> throw new BadRequestException("INVALID_STATUS", "El estado debe ser active o archived.");
        };
        return notificationService.list(accountId(authentication), archived, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.unreadCount(accountId(authentication)));
    }

    @PatchMapping("/{notificationId}/read")
    public void markRead(@PathVariable UUID notificationId, Authentication authentication) {
        notificationService.markRead(accountId(authentication), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        notificationService.markAllRead(accountId(authentication));
    }

    @PostMapping("/{notificationId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID notificationId, Authentication authentication) {
        notificationService.archive(accountId(authentication), notificationId);
    }

    /** «Limpiar»: pasa al histórico las ya leídas. */
    @PostMapping("/archive-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveRead(Authentication authentication) {
        notificationService.archiveRead(accountId(authentication));
    }

    private UUID accountId(Authentication authentication) {
        return accountLookup.findByEmail(authentication.getName()).map(AccountRef::accountId)
                .orElseThrow(() -> new UnauthorizedException("UNAUTHORIZED", "Tu sesión no es válida."));
    }
}
