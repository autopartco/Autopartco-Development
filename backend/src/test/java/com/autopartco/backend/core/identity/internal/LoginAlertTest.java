package com.autopartco.backend.core.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.autopartco.backend.core.identity.LoginResponse;

class LoginAlertTest {
    private static final String CHROME_120 =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CHROME_121 =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.85 Safari/537.36";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1";

    private final AccountSessionRepository sessionRepository = mock(AccountSessionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T20:30:00Z"), ZoneOffset.UTC);
    private final LoginAlerts loginAlerts = new LoginAlerts(sessionRepository, eventPublisher, clock);
    private Account account;

    @BeforeEach
    void setUp() {
        Person person = new Person();
        person.setName("Ana");
        person.setSurname("Pérez");
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("ana@example.com");
        account.setPerson(person);
        account.setConfiguration(Map.of());
    }

    @Test
    void firstLoginOfTheAccountDoesNotAlert() {
        when(sessionRepository.findDistinctUserAgentsByAccountId(account.getId())).thenReturn(List.of());

        assertThat(loginAlerts.shouldAlert(account, CHROME_120)).isFalse();
    }

    @Test
    void knownUserAgentDoesNotAlertEvenAfterABrowserUpdate() {
        when(sessionRepository.findDistinctUserAgentsByAccountId(account.getId())).thenReturn(List.of(CHROME_120));

        assertThat(loginAlerts.shouldAlert(account, CHROME_121)).isFalse();
    }

    @Test
    void newUserAgentAlerts() {
        when(sessionRepository.findDistinctUserAgentsByAccountId(account.getId())).thenReturn(List.of(CHROME_120));

        assertThat(loginAlerts.shouldAlert(account, IPHONE_SAFARI)).isTrue();
    }

    @Test
    void disabledLoginAlertsDoNotAlert() {
        account.setConfiguration(Map.of("security", Map.of("loginAlerts", false)));

        assertThat(loginAlerts.shouldAlert(account, IPHONE_SAFARI)).isFalse();
        verify(sessionRepository, never()).findDistinctUserAgentsByAccountId(any());
    }

    @Test
    void alertUsesBogotaTimeApproximateDeviceAndMaskedIp() {
        loginAlerts.publish(account, "190.24.10.77", IPHONE_SAFARI);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        LoginAlertRequested alert = (LoginAlertRequested) event.getValue();
        assertEquals("ana@example.com", alert.email());
        assertEquals("Safari en iOS", alert.device());
        assertEquals("190.24.10.***", alert.maskedIp());
        assertThat(alert.when()).startsWith("11 de noviembre de 2026, 3:30");
    }

    @Test
    void describesAndMasks() {
        assertEquals("Chrome en Windows", LoginAlerts.describe(CHROME_120));
        assertEquals("2001:db8:85a3:***", LoginAlerts.maskIp("2001:db8:85a3:0:0:8a2e:370:7334"));
        assertEquals("desconocida", LoginAlerts.maskIp(null));
    }

    @Test
    void sessionOpenerPublishesOnlyWhenTheUserAgentIsNew() {
        AuthTokenService authTokenService = mock(AuthTokenService.class);
        AccountSessionService accountSessionService = mock(AccountSessionService.class);
        LoginResponse tokens = new LoginResponse("access", "refresh", "Bearer", 900);
        when(authTokenService.issue(account))
                .thenReturn(new AuthTokenService.TokenPair(tokens, LocalDateTime.now().plusDays(7), UUID.randomUUID()));
        SessionOpener opener = new SessionOpener(authTokenService, accountSessionService, loginAlerts);

        when(sessionRepository.findDistinctUserAgentsByAccountId(account.getId())).thenReturn(List.of(CHROME_120));
        opener.open(account, "190.24.10.77", CHROME_121);
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        opener.open(account, "190.24.10.77", IPHONE_SAFARI);
        verify(eventPublisher).publishEvent(any(LoginAlertRequested.class));
    }
}
