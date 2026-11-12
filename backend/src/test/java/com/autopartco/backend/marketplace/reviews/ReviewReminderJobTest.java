package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.settings.AccountSettings;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.shared.config.AppProperties;

/** Recordatorios: un correo por pedido, respeta la preferencia y marca cada venta para no repetir. */
class ReviewReminderJobTest {
    private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
    private final EmailService email = mock(EmailService.class);
    private final SettingsQueries settings = mock(SettingsQueries.class);
    private final AppProperties app = new AppProperties("https://api.autopartco.co", "https://autopartco.co/", null, null, null);
    private final ReviewReminderJob job = new ReviewReminderJob(jdbc, email, settings, app);
    private final UUID orderId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private void reminders(boolean enabled) {
        when(settings.accountSettings(accountId)).thenReturn(new AccountSettings(null,
                new AccountSettings.Notifications(true, true, true, false, enabled), null));
    }

    private ReviewReminderJob.Due sale(UUID saleId) {
        return new ReviewReminderJob.Due(saleId, orderId, 1042L, accountId, "ana@correo.co", "Ana María");
    }

    @Test
    void oneEmailPerOrderEvenWithSeveralPendingSalesAndEachSaleIsMarked() {
        reminders(true);

        int sent = job.process(1, List.of(sale(UUID.randomUUID()), sale(UUID.randomUUID())));

        assertThat(sent).isEqualTo(1);
        verify(email).sendReviewReminder("ana@correo.co", "Ana", 1042L,
                "https://autopartco.co/cuenta/calificar?pedido=" + orderId, "https://autopartco.co/cuenta/preferencias");
        verify(jdbc, times(2)).sql(org.mockito.ArgumentMatchers.contains("INSERT INTO review_reminders"));
    }

    @Test
    void turnedOffRemindersSendNothingButAreMarkedSoTheyAreNotReconsidered() {
        reminders(false);

        assertThat(job.process(2, List.of(sale(UUID.randomUUID())))).isZero();

        verify(email, never()).sendReviewReminder(any(), any(), anyLong(), any(), any());
        verify(jdbc).sql(org.mockito.ArgumentMatchers.contains("INSERT INTO review_reminders"));
    }

    @Test
    void aFailedEmailIsNotMarkedSoTheNextRunRetries() {
        reminders(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp")).when(email)
                .sendReviewReminder(anyString(), anyString(), anyLong(), anyString(), anyString());

        assertThat(job.process(1, List.of(sale(UUID.randomUUID())))).isZero();

        verify(jdbc, never()).sql(org.mockito.ArgumentMatchers.contains("INSERT INTO review_reminders"));
    }

    @Test
    void theQueryOnlyPicksSalesWithSomethingLeftToRateAndNoReminderYet() {
        assertThat(ReviewReminderJob.DUE_SQL)
                .contains("ss.code = 'DELIVERED'")
                .contains("rr.attempt >= :attempt")
                .contains("NOT EXISTS (SELECT 1 FROM store_reviews")
                .contains("NOT EXISTS (SELECT 1 FROM product_reviews");
        assertThat(ReviewReminderJob.FIRST_DAYS).isEqualTo(3);
        assertThat(ReviewReminderJob.SECOND_DAYS).isEqualTo(10);
        verify(email, never()).sendReviewReminder(any(), any(), anyLong(), any(), eq(""));
    }
}
