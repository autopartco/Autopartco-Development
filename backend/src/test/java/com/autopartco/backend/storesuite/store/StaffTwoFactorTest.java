package com.autopartco.backend.storesuite.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.SettingsService;
import com.autopartco.backend.shared.error.ForbiddenException;
import com.autopartco.backend.shared.error.NotFoundException;

class StaffTwoFactorTest {
    private static final String EMAIL = "colaborador@almacen.co";

    private final SettingsQueries settingsQueries = mock(SettingsQueries.class);
    private final AccountConfigurations accountConfigurations = mock(AccountConfigurations.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final StaffTwoFactorInterceptor interceptor =
            new StaffTwoFactorInterceptor(settingsQueries, accountConfigurations, storeAccess);

    private final UUID storeId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(EMAIL, null, "ROLE_STORE_MANAGER");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksCollaboratorWithoutTwoFactorWhenStoreRequiresIt() {
        requireStaffTwoFactor(true);
        account(false);
        when(storeAccess.isMember(personId, storeId)).thenReturn(true);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/products"), response(), null));

        assertEquals("STAFF_TWO_FACTOR_REQUIRED", error.getCode());
        assertEquals(Map.of("storeId", storeId.toString()), error.getDetails());
        // También la ruta raíz del almacén (PATCH del perfil).
        assertThrows(ForbiddenException.class,
                () -> interceptor.preHandle(request("PATCH", "/api/v1/stores/" + storeId), response(), null));
    }

    @Test
    void allowsSettingsSoTheOwnerCanTurnItOff() throws Exception {
        requireStaffTwoFactor(true);
        account(false);
        when(storeAccess.isMember(personId, storeId)).thenReturn(true);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/settings"), response(), null)).isTrue();
        assertThat(interceptor.preHandle(request("PATCH", "/api/v1/stores/" + storeId + "/settings"), response(), null)).isTrue();
        assertThat(interceptor.preHandle(request("GET", "/api/v1/account/settings"), response(), null)).isTrue();
        verifyNoInteractions(settingsQueries, accountConfigurations, storeAccess);
    }

    @Test
    void allowsCollaboratorWithTwoFactor() throws Exception {
        requireStaffTwoFactor(true);
        account(true);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/orders"), response(), null)).isTrue();
        verify(storeAccess, never()).isMember(any(), any());
    }

    @Test
    void storesWithoutTheRuleDoNotLookUpTheAccount() throws Exception {
        requireStaffTwoFactor(false);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/orders"), response(), null)).isTrue();
        verify(accountConfigurations, never()).securityStatus(anyString());
    }

    @Test
    void nonMembersFallThroughToTheUsualAuthorization() throws Exception {
        requireStaffTwoFactor(true);
        account(false);
        when(storeAccess.isMember(personId, storeId)).thenReturn(false);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/orders"), response(), null)).isTrue();
    }

    @Test
    void unknownStoreFallsThrough() throws Exception {
        when(settingsQueries.requiresStaffTwoFactor(storeId)).thenThrow(new NotFoundException("STORE_NOT_FOUND", "x"));

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores/" + storeId + "/orders"), response(), null)).isTrue();
    }

    @Test
    void storeSettingsReturn404ForAccountsOutsideTheStore() {
        SettingsService settingsService = mock(SettingsService.class);
        ManageStoreSettings manage = new ManageStoreSettings(storeAccess, accountConfigurations, settingsService);
        account(true);
        when(storeAccess.isMember(personId, storeId)).thenReturn(false);

        NotFoundException error = assertThrows(NotFoundException.class, () -> manage.get(storeId, EMAIL));

        assertEquals("STORE_NOT_FOUND", error.getCode());
        verifyNoInteractions(settingsService);
    }

    @Test
    void storeSettingsPatchPassesTheActorTwoFactorState() {
        SettingsService settingsService = mock(SettingsService.class);
        ManageStoreSettings manage = new ManageStoreSettings(storeAccess, accountConfigurations, settingsService);
        account(false);
        when(storeAccess.isMember(personId, storeId)).thenReturn(true);
        Map<String, Object> patch = Map.of("security", Map.of("requireStaffTwoFactor", true));

        manage.patch(storeId, EMAIL, patch);

        verify(storeAccess).requirePermission(EMAIL, storeId, "STORE_UPDATE");
        verify(settingsService).patchStore(storeId, accountId, false, patch);
    }

    private void requireStaffTwoFactor(boolean required) {
        when(settingsQueries.requiresStaffTwoFactor(storeId)).thenReturn(required);
    }

    private void account(boolean twoFactorEnabled) {
        when(accountConfigurations.securityStatus(EMAIL))
                .thenReturn(Optional.of(new AccountConfigurations.SecurityStatus(accountId, personId, twoFactorEnabled)));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
