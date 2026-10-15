package com.autopartco.backend.storesuite.onboarding;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import com.autopartco.backend.core.notification.SellerSetupMailer;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.shared.error.ErrorResponse;
import com.autopartco.backend.shared.error.GlobalExceptionHandler;
import com.autopartco.backend.shared.error.TooManyRequestsException;

@ExtendWith(MockitoExtension.class)
class RequestSetupLinkTest {
    @Mock
    private StoreApplications storeApplications;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private RequestSetupLink requestSetupLink;

    private final UUID applicationId = UUID.randomUUID();

    @Test
    void ownerRequestPublishesTheNewTokenForTheMailer() {
        when(storeApplications.reissueOnboardingTokenForOwner(applicationId, "ana@example.com"))
                .thenReturn(new StoreApplications.SetupLink("ana@example.com", "Ana", "new-token"));

        requestSetupLink.forOwner(applicationId, "ana@example.com");

        verify(events).publishEvent(new SetupLinkIssued("ana@example.com", "Ana", "new-token"));
    }

    @Test
    void ownerRequestInsideTheCooldownPublishesNothingAndMapsTo429() {
        when(storeApplications.reissueOnboardingTokenForOwner(applicationId, "ana@example.com"))
                .thenThrow(new TooManyRequestsException("SELLER_SETUP_LINK_COOLDOWN", "Espera"));

        TooManyRequestsException exception = assertThrows(
                TooManyRequestsException.class,
                () -> requestSetupLink.forOwner(applicationId, "ana@example.com")
        );

        verifyNoInteractions(events);
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleTooManyRequests(exception);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("SELLER_SETUP_LINK_COOLDOWN", response.getBody().error().code);
    }

    @Test
    void publicRequestWithAKnownTokenPublishesTheNewOne() {
        when(storeApplications.reissueOnboardingTokenByToken("old"))
                .thenReturn(Optional.of(new StoreApplications.SetupLink("ana@example.com", "Ana", "new-token")));

        requestSetupLink.byToken("old");

        verify(events).publishEvent(new SetupLinkIssued("ana@example.com", "Ana", "new-token"));
    }

    @Test
    void publicRequestWithAnUnknownTokenDoesNothingAndDoesNotFail() {
        when(storeApplications.reissueOnboardingTokenByToken("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> requestSetupLink.byToken("unknown"));

        verifyNoInteractions(events);
    }

    @Test
    void theEventNeverPrintsTheTokenNorTheRecipient() {
        String text = new SetupLinkIssued("ana@example.com", "Ana", "secret-token").toString();

        assertFalse(text.contains("secret-token"));
        assertFalse(text.contains("ana@example.com"));
    }

    @Test
    void controllerAlwaysAnswers204AndOnlyActsOnAValidBody() {
        var controller = new StoreApplicationController(null, null, requestSetupLink, null, null);
        when(storeApplications.reissueOnboardingTokenByToken("x")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.resendSetupLinkByToken(new SetupLinkResendRequest("x"), noErrors()).getStatusCode());
        verify(storeApplications).reissueOnboardingTokenByToken("x");

        BindingResult invalid = noErrors();
        invalid.reject("NotBlank");
        assertEquals(HttpStatus.NO_CONTENT, controller.resendSetupLinkByToken(new SetupLinkResendRequest(null), invalid).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.resendSetupLinkByToken(null, noErrors()).getStatusCode());
        Mockito.verifyNoMoreInteractions(storeApplications);
    }

    @Test
    void mailIsSentOnlyAfterCommitAndNeverOnRollback() {
        SellerSetupMailer mailer = Mockito.mock(SellerSetupMailer.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SellerSetupMailer.class, () -> mailer);
            context.register(TxConfig.class, SetupLinkMailListener.class);
            context.refresh();
            TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            SetupLinkIssued event = new SetupLinkIssued("ana@example.com", "Ana", "new-token");

            tx.executeWithoutResult(status -> {
                context.publishEvent(event);
                verify(mailer, never()).send(any(), any(), any());
                status.setRollbackOnly();
            });
            verify(mailer, never()).send(any(), any(), any());

            tx.executeWithoutResult(status -> context.publishEvent(event));
            verify(mailer).send("ana@example.com", "Ana", "new-token");
        }
    }

    @Test
    void mailFailuresAfterCommitAreSwallowed() {
        SellerSetupMailer mailer = Mockito.mock(SellerSetupMailer.class);
        doThrow(new IllegalStateException("smtp ana@example.com new-token")).when(mailer).send(any(), any(), any());

        assertDoesNotThrow(() -> new SetupLinkMailListener(mailer)
                .on(new SetupLinkIssued("ana@example.com", "Ana", "new-token")));
    }

    private static BindingResult noErrors() {
        return new BeanPropertyBindingResult(new SetupLinkResendRequest("x"), "request");
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoopTransactionManager();
        }
    }

    /** Gestor sin recurso real: basta para activar las sincronizaciones de commit/rollback. */
    static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
