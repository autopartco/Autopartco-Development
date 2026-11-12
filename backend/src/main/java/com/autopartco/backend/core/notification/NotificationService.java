package com.autopartco.backend.core.notification;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Bandeja in-app de cada cuenta. «Limpiar» archiva (no borra): las archivadas forman el histórico y se eliminan
 * pasados {@value #RETENTION_DAYS} días.
 */
@Service
@AllArgsConstructor
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final int MAX_TITLE = 150;
    static final int MAX_LINK = 300;
    static final int MAX_PAGE_SIZE = 50;
    static final int RETENTION_DAYS = 180;
    /**
     * Solo rutas internas del frontend ({@code /portal/…}): sin {@code //host}, {@code /\host}, URL absolutas, barras
     * invertidas ni espacios (el check de la BD es más laxo).
     */
    static final Pattern INTERNAL_LINK = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

    static final String INSERT_SQL =
            "INSERT INTO notifications (account_id, kind, title, message, link) VALUES (?, ?, ?, ?, ?)";
    private static final String OWN = " AND account_id = ? AND deleted_at IS NULL";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Notificación con enlace opcional a una ruta interna del frontend. El título se recorta a {@value #MAX_TITLE}
     * caracteres.
     *
     * @throws IllegalArgumentException si {@code link} no es una ruta interna o pasa de {@value #MAX_LINK} caracteres
     */
    @Transactional
    public void notify(UUID accountId, NotificationKind kind, String title, String message, String link) {
        if (link != null && (link.length() > MAX_LINK || !INTERNAL_LINK.matcher(link).matches())) {
            throw new IllegalArgumentException("Notification link must be an internal path.");
        }
        String safeTitle = title != null && title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE - 1) + "…" : title;
        jdbcTemplate.update(INSERT_SQL, accountId, (kind == null ? NotificationKind.SYSTEM : kind).name(), safeTitle,
                message, link);
    }

    /** La cuenta ya recibió alguna notificación con ese enlace (leída, archivada o no). */
    @Transactional(readOnly = true)
    public boolean exists(UUID accountId, String link) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM notifications WHERE account_id = ? AND link = ? AND deleted_at IS NULL)",
                Boolean.class, accountId, link));
    }

    /** Página (desde 1) de la bandeja activa o del histórico ({@code archived}), de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID accountId, boolean archived, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String filter = archived ? "archived_at IS NOT NULL" : "archived_at IS NULL";
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE " + filter + OWN, Long.class, accountId);
        List<NotificationResponse> items = jdbcTemplate.query("""
                SELECT id, kind, title, message, link, read_at, archived_at, created_at
                FROM notifications
                WHERE %s AND account_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(filter), (result, row) -> new NotificationResponse(
                result.getObject("id", UUID.class),
                kind(result.getString("kind")),
                result.getString("title"),
                result.getString("message"),
                result.getString("link"),
                result.getTimestamp("read_at") != null,
                result.getTimestamp("archived_at") != null,
                toLocalDateTime(result.getTimestamp("created_at"))
        ), accountId, safeSize, (long) (safePage - 1) * safeSize);
        return PageResponse.of(items, safePage, safeSize, total == null ? 0 : total);
    }

    /** No leídas de la bandeja activa (el histórico no cuenta). */
    @Transactional(readOnly = true)
    public long unreadCount(UUID accountId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE read_at IS NULL AND archived_at IS NULL" + OWN,
                Long.class, accountId);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(UUID accountId, UUID id) {
        requireFound(jdbcTemplate.update(
                "UPDATE notifications SET read_at = COALESCE(read_at, now()) WHERE id = ?" + OWN, id, accountId));
    }

    /** Marca como leídas todas las de la bandeja activa; devuelve cuántas cambiaron. */
    @Transactional
    public int markAllRead(UUID accountId) {
        return jdbcTemplate.update(
                "UPDATE notifications SET read_at = now() WHERE read_at IS NULL AND archived_at IS NULL" + OWN,
                accountId);
    }

    /** Pasa una notificación al histórico (y la da por leída). Idempotente; 404 si no es de la cuenta. */
    @Transactional
    public void archive(UUID accountId, UUID id) {
        requireFound(jdbcTemplate.update(
                "UPDATE notifications SET archived_at = COALESCE(archived_at, now()), read_at = COALESCE(read_at, now())"
                        + " WHERE id = ?" + OWN, id, accountId));
    }

    /** «Limpiar»: archiva las leídas de la bandeja activa; devuelve cuántas. */
    @Transactional
    public int archiveRead(UUID accountId) {
        return jdbcTemplate.update(
                "UPDATE notifications SET archived_at = now() WHERE read_at IS NOT NULL AND archived_at IS NULL" + OWN,
                accountId);
    }

    /** Borra del histórico lo archivado hace más de {@value #RETENTION_DAYS} días. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    @Transactional
    public void purgeArchived() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM notifications WHERE archived_at < now() - make_interval(days => ?)", RETENTION_DAYS);
        if (deleted > 0) {
            log.info("Purged {} archived notifications", deleted);
        }
    }

    private static void requireFound(int updated) {
        if (updated == 0) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "No se encontró la notificación.");
        }
    }

    private static NotificationKind kind(String value) {
        try {
            return value == null ? NotificationKind.SYSTEM : NotificationKind.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return NotificationKind.SYSTEM;
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
