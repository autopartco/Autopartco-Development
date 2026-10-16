package com.autopartco.backend.core.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autopartco.backend.shared.config.AppProperties;

class SellerSetupMailerTest {
    @Test
    void theLinkPointsToTheFrontendSetupPageWithoutAnyQueryOrToken() {
        EmailService emailService = mock(EmailService.class);
        AppProperties properties = mock(AppProperties.class);
        org.mockito.Mockito.when(properties.frontendBaseUrl()).thenReturn("https://app.autopartco.test/");

        new SellerSetupMailer(emailService, properties).send("ana@example.com", "Ana");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSellerOnboarding(eq("ana@example.com"), eq("Ana"), link.capture());
        org.junit.jupiter.api.Assertions.assertEquals("https://app.autopartco.test/seller/setup", link.getValue());
        assertFalse(link.getValue().contains("?"));
    }
}
