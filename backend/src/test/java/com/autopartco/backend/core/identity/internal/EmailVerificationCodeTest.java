package com.autopartco.backend.core.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.web.VerifyEmailCodeRequest;
import com.autopartco.backend.core.identity.web.VerifyEmailResponse;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCodeTest {
    private static final String CODE_PURPOSE = EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION_CODE;
    private static final String EMAIL = "ana@example.com";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private StateRepository stateRepository;
    @Mock
    private AccountTokenRepository accountTokenRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthResponseMapper authResponseMapper;
    @Mock
    private AppProperties appProperties;

    private EmailVerificationTokenService tokenService;
    private EmailVerificationService service;
    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        tokenService = new EmailVerificationTokenService(accountTokenRepository, appProperties, new TokenMapper());
        service = new EmailVerificationService(accountRepository, stateRepository, tokenService,
                accountTokenRepository, auditService, authResponseMapper);
        accountId = UUID.randomUUID();
        account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(accountId);
        lenient().when(account.getEmail()).thenReturn(EMAIL);
    }

    @Test
    void correctCodeActivatesTheAccountAndInvalidatesPendingLinkAndCode() {
        stubAccount("PENDING");
        stubActive(codeToken("123456", 0));
        State active = state("ACTIVE");
        when(stateRepository.findByCode("ACTIVE")).thenReturn(Optional.of(active));
        VerifyEmailResponse expected = new VerifyEmailResponse(accountId, EMAIL, "ACTIVE", "ok");
        when(authResponseMapper.toVerifyEmailResponse(account, active)).thenReturn(expected);

        VerifyEmailResponse response = service.verifyByCode(new VerifyEmailCodeRequest(" Ana@Example.com ", "123456"));

        assertEquals(expected, response);
        verify(account).setState(active);
        verify(accountTokenRepository).markUsed(
                eq(accountId),
                eq(List.of(EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION, CODE_PURPOSE)),
                any(LocalDateTime.class));
        verify(auditService).recordEmailVerified(accountId, EMAIL, "ACTIVE");
    }

    @Test
    void wrongCodeCountsAnAttemptAndReturnsTheGenericError() {
        stubAccount("PENDING");
        AccountToken token = codeToken("123456", 0);
        stubActive(token);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyByCode(new VerifyEmailCodeRequest(EMAIL, "000000")));

        assertEquals("INVALID_VERIFICATION_CODE", error.getCode());
        assertEquals(1, token.getAttempts());
        assertFalse(token.isUsed());
        verify(accountTokenRepository).save(token);
        verify(account, never()).setState(any());
    }

    @Test
    void fifthFailureReturns429AndLeavesTheCodeInvalid() {
        stubAccount("PENDING");
        AccountToken token = codeToken("123456", 4);
        stubActive(token);

        TooManyRequestsException error = assertThrows(TooManyRequestsException.class,
                () -> service.verifyByCode(new VerifyEmailCodeRequest(EMAIL, "000000")));

        assertEquals("VERIFICATION_ATTEMPTS_EXCEEDED", error.getCode());
        assertEquals(5, token.getAttempts());
        assertTrue(token.isUsed());
        verify(accountTokenRepository).save(token);
    }

    @Test
    void unknownEmailReturnsTheSameGenericError() {
        when(accountRepository.lockByEmail("nadie@example.com")).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyByCode(new VerifyEmailCodeRequest("nadie@example.com", "123456")));

        assertEquals("INVALID_VERIFICATION_CODE", error.getCode());
        assertEquals("El código no es válido o expiró.", error.getMessage());
        verify(accountTokenRepository, never()).findActive(any(), any(), any());
    }

    @Test
    void alreadyActiveAccountReturnsTheGenericError() {
        stubAccount("ACTIVE");

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyByCode(new VerifyEmailCodeRequest(EMAIL, "123456")));

        assertEquals("INVALID_VERIFICATION_CODE", error.getCode());
        verify(accountTokenRepository, never()).markUsed(any(), anyCollection(), any());
    }

    @Test
    void missingOrExpiredCodeReturnsTheGenericError() {
        stubAccount("PENDING");
        when(accountTokenRepository.findActive(eq(accountId), eq(CODE_PURPOSE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyByCode(new VerifyEmailCodeRequest(EMAIL, "123456")));

        assertEquals("INVALID_VERIFICATION_CODE", error.getCode());
    }

    @Test
    void linkVerificationAlsoInvalidatesThePendingCode() {
        AccountToken linkToken = AccountToken.builder()
                .account(account).purpose("EMAIL_VERIFICATION").token("hash")
                .expirationAt(LocalDateTime.now().plusMinutes(10)).used(false).build();
        when(accountTokenRepository.findByTokenAndPurpose(any(), eq("EMAIL_VERIFICATION")))
                .thenReturn(Optional.of(linkToken));
        State active = state("ACTIVE");
        when(stateRepository.findByCode("ACTIVE")).thenReturn(Optional.of(active));

        service.verify("raw-token");

        verify(accountTokenRepository).markUsed(
                eq(accountId),
                eq(List.of(EmailVerificationTokenService.PURPOSE_EMAIL_VERIFICATION, CODE_PURPOSE)),
                any(LocalDateTime.class));
        assertTrue(linkToken.isUsed());
        verify(account).setState(active);
    }

    private void stubAccount(String stateCode) {
        when(account.getState()).thenReturn(state(stateCode));
        when(accountRepository.lockByEmail(EMAIL)).thenReturn(Optional.of(account));
    }

    private void stubActive(AccountToken token) {
        when(accountTokenRepository.findActive(eq(accountId), eq(CODE_PURPOSE), any(LocalDateTime.class)))
                .thenReturn(List.of(token));
    }

    private AccountToken codeToken(String code, int attempts) {
        return AccountToken.builder()
                .account(account)
                .purpose(CODE_PURPOSE)
                .token(tokenService.hashVerificationCode(accountId, code))
                .expirationAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .attempts((short) attempts)
                .build();
    }

    private static State state(String code) {
        State state = new State();
        state.setCode(code);
        return state;
    }
}
