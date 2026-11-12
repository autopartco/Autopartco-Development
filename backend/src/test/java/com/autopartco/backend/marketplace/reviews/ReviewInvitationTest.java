package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.events.SaleDelivered;

class ReviewInvitationTest {
    private final AuditService auditService = mock(AuditService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final AppProperties appProperties = new AppProperties("https://api.autopartco.co",
            "https://autopartco.co/", null, null, null);
    private final ReviewInvitationService service = spy(new ReviewInvitationService(null, auditService, emailService,
            appProperties));
    private final UUID orderId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private void deliveredOrder() {
        doReturn(Optional.of(new ReviewInvitationService.Recipient(accountId, "ana@correo.co", "Ana María", 1042)))
                .when(service).recipient(orderId);
    }

    @Test
    void sendsTheInvitationWithTheOrderLinkAndMarksIt() {
        deliveredOrder();

        assertThat(service.invite(orderId)).isTrue();

        verify(emailService).sendReviewInvitation("ana@correo.co", "Ana", 1042,
                "https://autopartco.co/cuenta/calificar?pedido=" + orderId);
        verify(auditService).record(eq(accountId), eq("REVIEW_INVITATION_SENT"), eq("ORDER"), eq(orderId), anyMap());
    }

    @Test
    void sendsOnlyOncePerOrder() {
        deliveredOrder();
        when(auditService.exists("REVIEW_INVITATION_SENT", "ORDER", orderId)).thenReturn(false, true);

        assertThat(service.invite(orderId)).isTrue();
        assertThat(service.invite(orderId)).isFalse();

        verify(emailService, org.mockito.Mockito.times(1)).sendReviewInvitation(anyString(), anyString(), anyLong(),
                anyString());
        verify(auditService, org.mockito.Mockito.times(1)).record(any(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void doesNothingWithoutADeliveredOrderOrBuyer() {
        doReturn(Optional.empty()).when(service).recipient(orderId);

        assertThat(service.invite(orderId)).isFalse();

        verify(emailService, never()).sendReviewInvitation(anyString(), anyString(), anyLong(), anyString());
        verify(auditService, never()).record(any(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void aFailedSendIsNotMarkedSoTheNextDeliveryCanRetry() {
        deliveredOrder();
        doThrow(new org.springframework.mail.MailSendException("smtp")).when(emailService)
                .sendReviewInvitation(anyString(), anyString(), anyLong(), anyString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.invite(orderId))
                .isInstanceOf(org.springframework.mail.MailSendException.class);

        verify(auditService, never()).record(any(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void listenerRunsAsyncAfterCommitAndSwallowsFailures() throws NoSuchMethodException {
        Method on = ReviewInvitationListener.class.getDeclaredMethod("on", SaleDelivered.class);
        assertThat(on.getAnnotation(Async.class)).isNotNull();
        assertThat(on.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);

        ReviewInvitationService invitations = mock(ReviewInvitationService.class);
        when(invitations.invite(orderId)).thenThrow(new IllegalStateException("smtp ana@correo.co"));
        new ReviewInvitationListener(invitations).on(new SaleDelivered(UUID.randomUUID(), orderId));

        verify(invitations).invite(orderId);
    }

    @Test
    void recipientQueryRequiresADeliveredSale() {
        assertThat(ReviewInvitationService.RECIPIENT_SQL).contains("st.code = 'DELIVERED'").contains("LIMIT 1");
    }
}
