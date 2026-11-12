package com.autopartco.backend.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

/** Bandeja: paginación, archivado (histórico), marcar todo y alcance por cuenta. */
class NotificationInboxTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NotificationService service = new NotificationService(jdbcTemplate);
    private final UUID accountId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void listClampsPageAndSizeAndFiltersByStatusAndAccount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(accountId))).thenReturn(120L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(accountId), eq(50), eq(50L))).thenReturn(List.of());

        PageResponse<NotificationResponse> page = service.list(accountId, true, 2, 500);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
        assertThat(page.total()).isEqualTo(120);
        assertThat(page.totalPages()).isEqualTo(3);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(accountId), eq(50), eq(50L));
        assertThat(sql.getValue()).contains("archived_at IS NOT NULL").contains("account_id = ?");
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeInboxStartsAtPageOne() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(accountId))).thenReturn(0L);

        PageResponse<NotificationResponse> page = service.list(accountId, false, 0, 0);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(accountId), eq(1), eq(0L));
        assertThat(sql.getValue()).contains("archived_at IS NULL").doesNotContain("IS NOT NULL");
    }

    @Test
    void unreadCountIgnoresArchived() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(accountId))).thenReturn(4L);

        assertThat(service.unreadCount(accountId)).isEqualTo(4);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), eq(accountId));
        assertThat(sql.getValue()).contains("read_at IS NULL").contains("archived_at IS NULL").contains("account_id = ?");
    }

    @Test
    void archivingSomeoneElsesNotificationIsNotFound() {
        when(jdbcTemplate.update(anyString(), eq(notificationId), eq(accountId))).thenReturn(0);

        assertThatThrownBy(() -> service.archive(accountId, notificationId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("NOTIFICATION_NOT_FOUND"));
        assertThatThrownBy(() -> service.markRead(accountId, notificationId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void archiveMarksAsReadAndIsScopedToTheAccount() {
        when(jdbcTemplate.update(anyString(), eq(notificationId), eq(accountId))).thenReturn(1);

        service.archive(accountId, notificationId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(notificationId), eq(accountId));
        assertThat(sql.getValue()).contains("archived_at = COALESCE(archived_at, now())")
                .contains("read_at = COALESCE(read_at, now())").contains("account_id = ?");
    }

    @Test
    void readAllAndClearReadOnlyTouchTheActiveInboxOfTheAccount() {
        when(jdbcTemplate.update(anyString(), eq(accountId))).thenReturn(3, 2);

        assertThat(service.markAllRead(accountId)).isEqualTo(3);
        assertThat(service.archiveRead(accountId)).isEqualTo(2);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sql.capture(), eq(accountId));
        assertThat(sql.getAllValues().get(0)).contains("SET read_at = now()").contains("archived_at IS NULL");
        assertThat(sql.getAllValues().get(1)).contains("SET archived_at = now()").contains("read_at IS NOT NULL")
                .contains("archived_at IS NULL");
        assertThat(sql.getAllValues()).allSatisfy(statement -> assertThat(statement).contains("account_id = ?"));
    }

    @Test
    void purgeDeletesOnlyOldArchived() {
        service.purgeArchived();

        verify(jdbcTemplate).update(
                "DELETE FROM notifications WHERE archived_at < now() - make_interval(days => ?)",
                NotificationService.RETENTION_DAYS);
    }
}
