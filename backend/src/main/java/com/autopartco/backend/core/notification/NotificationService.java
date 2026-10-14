package com.autopartco.backend.core.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.autopartco.backend.shared.error.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void notify(UUID accountId, String title, String message) {
        jdbcTemplate.update(
                "INSERT INTO notifications (account_id, title, message) VALUES (?, ?, ?)",
                accountId, title, message);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listFor(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT notification.id, notification.title, notification.message,
                       notification.read_at, notification.created_at
                FROM notifications notification
                WHERE notification.account_id = ?
                ORDER BY notification.created_at DESC
                LIMIT 50
                """, (result, row) -> new NotificationResponse(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("message"),
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
