package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.web.ResendVerificationRequest;
import com.autopartco.backend.core.identity.web.ResendVerificationResponse;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.persistence.State;

@ExtendWith(MockitoExtension.class)
class ResendVerificationTest {
    private static final String PURPOSE = EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountTokenRepository accountTokenRepository;
    @Mock
    private EmailVerificationTokenService tokenService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuditService auditService;
    @Mock
    private AppProperties appProperties;

    private ResendVerificationService service;
    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new ResendVerificationService(accountRepository, accountTokenRepository, tokenService,
                eventPublisher, auditService, appProperties);
        accountId = UUID.randomUUID();
        account = mock(Account.class);
    }

    @Test
    void resendsToAPendingAccountInvalidatingPreviousTokens() {
        stubPending();
        when(accountTokenRepository.findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(accountId, PURPOSE))
                .thenReturn(Optional.of(tokenCreated(LocalDateTime.now().minusMinutes(5))));
        when(accountTokenRepository.countByAccount_IdAndPurposeAndCreatedAtAfter(eq(accountId), eq(PURPOSE), any()))
                .thenReturn(1L);
        when(tokenService.issueVerification(account)).thenReturn(new EmailVerificationTokenService.IssuedVerification(
                "raw", LocalDateTime.now().plusHours(1), "123456", LocalDateTime.now().plusMinutes(30)));
        when(tokenService.verificationLink("raw")).thenReturn("https://app/verify-email?token=raw");
        when(tokenService.codeValidity()).thenReturn(Duration.ofMinutes(30));
        when(appProperties.emailVerification()).thenReturn(new AppProperties.EmailVerification(60, "no-reply@x"));
        Person person = new Person();
        person.setName("Ana");
        person.setSurname("Perez");
        when(account.getPerson()).thenReturn(person);
        when(account.getEmail()).thenReturn("ana@example.com");

        ResendVerificationResponse response = service.resend(new ResendVerificationRequest("Ana@Example.com"));

        assertResponse(response);
        verify(tokenService).invalidatePendingVerification(accountId);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        EmailVerificationRequested sent = (EmailVerificationRequested) event.getValue();
        assertEquals("123456", sent.code());
        assertEquals("ana@example.com", sent.email());
        verify(auditService).record(eq(accountId), eq("EMAIL_VERIFICATION_RESENT"), any(), any(), any());
    }

    @Test
    void doesNotSendWithinTheSixtySecondCooldownButStillAnswers() {
        stubPending();
        when(accountTokenRepository.findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(accountId, PURPOSE))
                .thenReturn(Optional.of(tokenCreated(LocalDateTime.now().minusSeconds(10))));

        assertResponse(service.resend(new ResendVerificationRequest("ana@example.com")));
        verifyNothingSent();
    }

    @Test
    void doesNotSendAfterFiveSendsInTheLastHour() {
        stubPending();
        when(accountTokenRepository.findFirstByAccount_IdAndPurposeOrderByCreatedAtDesc(accountId, PURPOSE))
                .thenReturn(Optional.of(tokenCreated(LocalDateTime.now().minusMinutes(5))));
        when(accountTokenRepository.countByAccount_IdAndPurposeAndCreatedAtAfter(eq(accountId), eq(PURPOSE), any()))
                .thenReturn(5L);

        assertResponse(service.resend(new ResendVerificationRequest("ana@example.com")));
        verifyNothingSent();
    }

    @Test
    void unknownEmailAnswersTheSameAndSendsNothing() {
        when(accountRepository.lockByEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertResponse(service.resend(new ResendVerificationRequest("nadie@example.com")));
        verifyNothingSent();
    }

    @Test
    void alreadyVerifiedAccountSendsNothing() {
        State active = new State();
        active.setCode("ACTIVE");
        when(account.getState()).thenReturn(active);
        when(accountRepository.lockByEmail("ana@example.com")).thenReturn(Optional.of(account));

        assertResponse(service.resend(new ResendVerificationRequest("ana@example.com")));
        verifyNothingSent();
    }

    private void assertResponse(ResendVerificationResponse response) {
        assertEquals("Si la cuenta existe y no está verificada, te enviamos un correo.", response.message());
        assertEquals(60, response.retryAfterSeconds());
    }

    private void verifyNothingSent() {
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(tokenService, never()).issueVerification(any());
        verify(accountTokenRepository, never()).markUsed(any(), anyCollection(), any());
    }

    private void stubPending() {
        State pending = new State();
        pending.setCode("PENDING");
        when(account.getState()).thenReturn(pending);
        when(account.getId()).thenReturn(accountId);
        when(accountRepository.lockByEmail("ana@example.com")).thenReturn(Optional.of(account));
    }

    private AccountToken tokenCreated(LocalDateTime createdAt) {
        AccountToken token = new AccountToken();
        token.setCreatedAt(createdAt);
        return token;
    }
}
