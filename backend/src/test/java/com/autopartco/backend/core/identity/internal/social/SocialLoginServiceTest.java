package com.autopartco.backend.core.identity.internal.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.LoginResponse;
import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.AccountStatusService;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.AuthTokenService;
import com.autopartco.backend.core.identity.internal.LoginAttemptService;
import com.autopartco.backend.core.identity.internal.Person;
import com.autopartco.backend.core.identity.internal.PersonRepository;
import com.autopartco.backend.core.identity.web.SocialLoginRequest;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.UnauthorizedException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

class SocialLoginServiceTest {
    private final SocialTokenVerifier google = mock(SocialTokenVerifier.class);
    private final SocialTokenVerifier facebook = mock(SocialTokenVerifier.class);
    private final AccountIdentityRepository identityRepository = mock(AccountIdentityRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final AccountTypeRepository accountTypeRepository = mock(AccountTypeRepository.class);
    private final StateRepository stateRepository = mock(StateRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountStatusService accountStatusService = mock(AccountStatusService.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuthTokenService authTokenService = mock(AuthTokenService.class);
    private final AccountSessionService accountSessionService = mock(AccountSessionService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final LoginResponse tokens = new LoginResponse("access", "refresh", "Bearer", 900);

    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        when(google.provider()).thenReturn(SocialProvider.GOOGLE);
        when(google.enabled()).thenReturn(true);
        when(facebook.provider()).thenReturn(SocialProvider.FACEBOOK);
        when(facebook.enabled()).thenReturn(true);
        when(authTokenService.issue(any(Account.class)))
                .thenReturn(new AuthTokenService.TokenPair(tokens, LocalDateTime.now().plusDays(7), UUID.randomUUID()));
        service = new SocialLoginService(List.of(google, facebook), identityRepository, accountRepository, personRepository,
                accountTypeRepository, stateRepository, passwordEncoder, accountStatusService, loginAttemptService,
                authTokenService, accountSessionService, auditService);
    }

    @Test
    void linkedIdentityOpensSession() {
        Account account = account(StateCodes.ACTIVE);
        when(google.verify("tok")).thenReturn(identity(SocialProvider.GOOGLE, "ana@example.com", true));
        when(identityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(AccountIdentity.builder().account(account).provider("google").subject("sub-1").build()));

        LoginResponse response = service.login(SocialProvider.GOOGLE, new SocialLoginRequest("tok", null), "1.1.1.1", "UA");

        assertSame(tokens, response);
        verify(loginAttemptService).record(account, true, "1.1.1.1");
        verify(auditService).record(eq(account.getId()), eq("SOCIAL_LOGIN"), eq("ACCOUNT"), eq(account.getId()), any());
    }

    @Test
    void googleVerifiedEmailLinksExistingAccount() {
        Account account = account(StateCodes.ACTIVE);
        when(google.verify("tok")).thenReturn(identity(SocialProvider.GOOGLE, "Ana@Example.com", true));
        when(identityRepository.findByProviderAndSubject("google", "sub-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(account));

        service.login(SocialProvider.GOOGLE, new SocialLoginRequest("tok", null), "1.1.1.1", "UA");

        ArgumentCaptor<AccountIdentity> saved = ArgumentCaptor.forClass(AccountIdentity.class);
        verify(identityRepository).save(saved.capture());
        assertEquals("google", saved.getValue().getProvider());
        assertEquals("sub-1", saved.getValue().getSubject());
        assertSame(account, saved.getValue().getAccount());
        verify(auditService).record(eq(account.getId()), eq("SOCIAL_LINKED"), eq("ACCOUNT"), eq(account.getId()), any());
    }

    @Test
    void googleUnverifiedEmailDoesNotLinkExistingAccount() {
        when(google.verify("tok")).thenReturn(identity(SocialProvider.GOOGLE, "ana@example.com", false));
        when(identityRepository.findByProviderAndSubject("google", "sub-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(account(StateCodes.ACTIVE)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.login(SocialProvider.GOOGLE, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_LINK_REQUIRED", error.getCode());
        verify(identityRepository, never()).save(any());
        verify(authTokenService, never()).issue(any(Account.class));
    }

    @Test
    void facebookNeverLinksExistingAccountByEmail() {
        when(facebook.verify("tok")).thenReturn(identity(SocialProvider.FACEBOOK, "ana@example.com", false));
        when(identityRepository.findByProviderAndSubject("facebook", "sub-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(account(StateCodes.ACTIVE)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.login(SocialProvider.FACEBOOK, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_LINK_REQUIRED", error.getCode());
        verify(identityRepository, never()).save(any());
    }

    @Test
    void newPersonWithoutRegistrationGetsPrefill() {
        when(facebook.verify("tok")).thenReturn(identity(SocialProvider.FACEBOOK, "nuevo@example.com", false));
        when(identityRepository.findByProviderAndSubject("facebook", "sub-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("nuevo@example.com")).thenReturn(Optional.empty());

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.login(SocialProvider.FACEBOOK, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_REGISTRATION_REQUIRED", error.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) error.getDetails();
        assertEquals("nuevo@example.com", details.get("email"));
        assertEquals("Ana", details.get("name"));
    }

    @Test
    void registrationCreatesActiveCustomerWithUnusablePassword() {
        State active = new State();
        active.setCode(StateCodes.ACTIVE);
        when(facebook.verify("tok")).thenReturn(identity(SocialProvider.FACEBOOK, "nuevo@example.com", false));
        when(identityRepository.findByProviderAndSubject("facebook", "sub-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("nuevo@example.com")).thenReturn(Optional.empty());
        when(personRepository.existsByIdentification("123456")).thenReturn(false);
        when(accountTypeRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(new AccountType()));
        when(stateRepository.findByCode(StateCodes.ACTIVE)).thenReturn(Optional.of(active));
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        SocialLoginRequest.Registration registration =
                new SocialLoginRequest.Registration(null, null, "123456", LocalDate.of(1990, 1, 1), "3001234567");
        LoginResponse response = service.login(SocialProvider.FACEBOOK, new SocialLoginRequest("tok", registration), "1.1.1.1", "UA");

        assertSame(tokens, response);
        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(account.capture());
        assertEquals("nuevo@example.com", account.getValue().getEmail());
        assertEquals("encoded-random", account.getValue().getPassword());
        assertSame(active, account.getValue().getState());
        verify(identityRepository).save(any(AccountIdentity.class));
        verify(auditService).record(any(), eq("USER_REGISTERED_SOCIAL"), eq("ACCOUNT"), any(), any());
    }

    @Test
    void missingEmailIsRejected() {
        when(facebook.verify("tok")).thenReturn(identity(SocialProvider.FACEBOOK, null, false));
        when(identityRepository.findByProviderAndSubject("facebook", "sub-1")).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.login(SocialProvider.FACEBOOK, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_EMAIL_REQUIRED", error.getCode());
    }

    @Test
    void invalidTokenPropagatesUnauthorized() {
        when(google.verify("bad")).thenThrow(SocialTokenVerifier.invalidToken());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.login(SocialProvider.GOOGLE, new SocialLoginRequest("bad", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_TOKEN_INVALID", error.getCode());
        verify(identityRepository, never()).findByProviderAndSubject(anyString(), anyString());
    }

    @Test
    void lockedOrInactiveAccountCannotSignIn() {
        Account pending = account(StateCodes.PENDING);
        when(google.verify("tok")).thenReturn(identity(SocialProvider.GOOGLE, "ana@example.com", true));
        when(identityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(AccountIdentity.builder().account(pending).provider("google").subject("sub-1").build()));

        assertThrows(UnauthorizedException.class,
                () -> service.login(SocialProvider.GOOGLE, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));
        verify(authTokenService, never()).issue(any(Account.class));
    }

    @Test
    void disabledProviderIsNotFound() {
        when(google.enabled()).thenReturn(false);

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.login(SocialProvider.GOOGLE, new SocialLoginRequest("tok", null), "1.1.1.1", "UA"));

        assertEquals("SOCIAL_PROVIDER_DISABLED", error.getCode());
    }

    private static SocialIdentity identity(SocialProvider provider, String email, boolean verified) {
        return new SocialIdentity(provider, "sub-1", email, verified, "Ana", "Pérez");
    }

    private static Account account(String stateCode) {
        State state = new State();
        state.setCode(stateCode);
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("ana@example.com");
        account.setState(state);
        return account;
    }
}
