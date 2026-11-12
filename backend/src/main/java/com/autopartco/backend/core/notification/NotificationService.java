package com.autopartco.backend.core.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class NotificationService {
    static final int MAX_TITLE = 150;
    static final int MAX_LINK = 300;
    /**
     * Solo rutas internas del frontend ({@code /portal/…}): sin {@code //host}, {@code /\host}, URL absolutas, barras
     * invertidas ni espacios (el check de la BD es más laxo).
     */
    static final Pattern INTERNAL_LINK = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void notify(UUID accountId, String title, String message) {
        notify(accountId, title, message, null);
    }

    /**
     * Notificación con enlace a una ruta interna del frontend. El título se recorta a {@value #MAX_TITLE} caracteres.
     *
     * @throws IllegalArgumentException si {@code link} no es una ruta interna o pasa de {@value #MAX_LINK} caracteres
     */
    @Transactional
    public void notify(UUID accountId, String title, String message, String link) {
        if (link != null && (link.length() > MAX_LINK || !INTERNAL_LINK.matcher(link).matches())) {
            throw new IllegalArgumentException("Notification link must be an internal path.");
        }
        String safeTitle = title != null && title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE - 1) + "…" : title;
        jdbcTemplate.update(
                "INSERT INTO notifications (account_id, title, message, link) VALUES (?, ?, ?, ?)",
                accountId, safeTitle, message, link);
    }

    /** La cuenta ya recibió alguna notificación con ese enlace (leída o no). */
    @Transactional(readOnly = true)
    public boolean exists(UUID accountId, String link) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM notifications WHERE account_id = ? AND link = ?)", Boolean.class,
                accountId, link));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listFor(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT notification.id, notification.title, notification.message, notification.link,
                       notification.read_at, notification.created_at
                FROM notifications notification
                WHERE notification.account_id = ?
                ORDER BY notification.created_at DESC
                LIMIT 50
                """, (result, row) -> new NotificationResponse(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("message"),
                result.getString("link"),
                result.getTimestamp("read_at") != null,
                toLocalDateTime(result.getTimestamp("created_at"))
        ), accountId);
    }

    @Transactional
    public void markRead(UUID accountId, UUID id) {
        int updated = jdbcTemplate.update("""
                UPDATE notifications
                SET read_at = COALESCE(read_at, now())
                WHERE id = ? AND account_id = ?
                """, id, accountId);
        if (updated == 0) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "No se encontró la notificación.");
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
