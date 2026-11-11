package com.autopartco.backend.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.identity.AccountConfigurations;
import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.organization.StoreConfigurations;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ForbiddenException;

class SettingsServiceTest {
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ListingQueries listingQueries = mock(ListingQueries.class);
    private final AuditService auditService = mock(AuditService.class);

    private final UUID accountId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private Account account;
    private Store store;
    private SettingsService service;

    @BeforeEach
    void setUp() {
        AccountConfigurations accountConfigurations = new AccountConfigurations(accountRepository);
        StoreConfigurations storeConfigurations = new StoreConfigurations(storeRepository);
        SettingsQueries queries = new SettingsQueries(accountConfigurations, storeConfigurations);
        service = new SettingsService(accountConfigurations, storeConfigurations, listingQueries, queries, auditService,
                mock(org.springframework.context.ApplicationEventPublisher.class));

        account = new Account();
        account.setId(accountId);
        account.setConfiguration(Map.of());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.lockById(accountId)).thenReturn(Optional.of(account));

        Map<String, Object> storeConfiguration = new HashMap<>();
        storeConfiguration.put("description", "Repuestos para Chevrolet");
        storeConfiguration.put("publicProfile", Map.of("whatsapp", "+573001234567", "instagram", "repuestos"));
        store = new Store();
        store.setId(storeId);
        store.setConfiguration(storeConfiguration);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(storeRepository.lockById(storeId)).thenReturn(Optional.of(store));
    }

    @Test
    void accountReadReturnsDefaults() {
        AccountSettings settings = service.accountSettings(accountId);

        assertThat(settings.security().twoFactor().enabled()).isFalse();
        assertEquals("EMAIL", settings.security().twoFactor().method());
        assertThat(settings.security().loginAlerts()).isTrue();
        assertThat(settings.notifications().orderUpdates()).isTrue();
        assertThat(settings.notifications().marketing()).isFalse();
        assertEquals("system", settings.preferences().theme());
        assertThat(settings.preferences().defaultCityCode()).isNull();
    }

    @Test
    void storeReadReturnsDefaultsAndMergesStoredValues() {
        store.getConfiguration().put("profile", Map.of("tagline", "Todo para tu carro", "bogus", 1));
        store.getConfiguration().put("catalog", Map.of("showExactStock", "yes"));

        StoreSettings settings = service.storeSettings(storeId);

        assertThat(settings.contact().whatsappEnabled()).isTrue();
        assertThat(settings.contact().whatsappMessage()).contains("{enlace}");
        assertEquals("Todo para tu carro", settings.profile().tagline());
        assertThat(settings.profile().showBranchesMap()).isTrue();
        assertThat(settings.profile().featuredProductIds()).isEmpty();
        assertThat(settings.catalog().showExactStock()).isFalse();
        assertThat(settings.notifications().recipients()).isEmpty();
        assertThat(settings.security().requireStaffTwoFactor()).isFalse();
    }

    @Test
    void unknownKeyIsRejected() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.patchStore(storeId, actorId, false, Map.of("contact", Map.of("telegram", true))));
        assertEquals("UNKNOWN_SETTING", error.getCode());
        assertEquals(Map.of("key", "contact.telegram"), error.getDetails());

        BadRequestException rootError = assertThrows(BadRequestException.class,
                () -> service.patchAccount(accountId, Map.of("description", "x")));
        assertEquals("UNKNOWN_SETTING", rootError.getCode());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void whatsappMessageWithoutLinkIsRejected() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.patchStore(storeId, actorId,
                false, Map.of("contact", Map.of("whatsappMessage", "Hola, quiero cotizar {producto}"))));

        assertEquals("INVALID_SETTING", error.getCode());
        assertEquals(Map.of("key", "contact.whatsappMessage"), error.getDetails());
    }

    @Test
    void whatsappMessageOver300IsRejected() {
        String longMessage = "{enlace} " + "a".repeat(300);
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.patchStore(storeId, actorId,
                false, Map.of("contact", Map.of("whatsappMessage", longMessage))));
        assertEquals("INVALID_SETTING", error.getCode());
    }

    @Test
    void storePatchKeepsDescriptionAndPublicProfile() {
        StoreSettings settings = service.patchStore(storeId, actorId, false,
                Map.of("profile", Map.of("tagline", "  Repuestos originales  ")));

        assertEquals("Repuestos originales", settings.profile().tagline());
        Map<String, Object> saved = store.getConfiguration();
        assertEquals("Repuestos para Chevrolet", saved.get("description"));
        assertEquals(Map.of("whatsapp", "+573001234567", "instagram", "repuestos"), saved.get("publicProfile"));
        assertThat(saved).containsKeys("contact", "profile", "catalog", "notifications", "security");
        verify(auditService).record(eq(actorId), eq("STORE_SETTINGS_UPDATED"), eq("STORE"), eq(storeId), any());
    }

    @Test
    void partialPatchKeepsOtherValues() {
        service.patchStore(storeId, actorId, false, Map.of("catalog", Map.of("showExactStock", true)));
        StoreSettings settings = service.patchStore(storeId, actorId, false,
                Map.of("contact", Map.of("whatsappEnabled", false)));

        assertThat(settings.catalog().showExactStock()).isTrue();
        assertThat(settings.contact().whatsappEnabled()).isFalse();
    }

    @Test
    void twoFactorCannotBeChangedByPatch() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.patchAccount(accountId,
                Map.of("security", Map.of("twoFactor", Map.of("enabled", true)))));
        assertEquals("TWO_FACTOR_REQUIRES_CONFIRMATION", error.getCode());

        // Reenviar el mismo valor (p. ej. el objeto completo leído antes) no es un cambio.
        AccountSettings settings = service.patchAccount(accountId, Map.of(
                "security", Map.of("twoFactor", Map.of("enabled", false, "method", "EMAIL"), "loginAlerts", false)));
        assertThat(settings.security().loginAlerts()).isFalse();
        assertThat(settings.security().twoFactor().enabled()).isFalse();
    }

    @Test
    void accountPatchValidatesThemeCityAndPreservesUnknownStoredKeys() {
        account.setConfiguration(Map.of("legacy", "keep"));

        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchAccount(accountId,
                Map.of("preferences", Map.of("theme", "blue")))).getCode());
        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchAccount(accountId,
                Map.of("preferences", Map.of("defaultCityCode", "1100")))).getCode());

        AccountSettings settings = service.patchAccount(accountId, Map.of(
                "preferences", Map.of("theme", "dark", "defaultCityCode", "11001"),
                "notifications", Map.of("marketing", true)));

        assertEquals("dark", settings.preferences().theme());
        assertEquals("11001", settings.preferences().defaultCityCode());
        assertThat(settings.notifications().marketing()).isTrue();
        assertEquals("keep", account.getConfiguration().get("legacy"));
        verify(auditService).record(eq(accountId), eq("ACCOUNT_SETTINGS_UPDATED"), eq("ACCOUNT"), eq(accountId), any());
    }

    @Test
    void recipientsAndTaglineAreLimited() {
        List<String> six = List.of("a@x.co", "b@x.co", "c@x.co", "d@x.co", "e@x.co", "f@x.co");
        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchStore(storeId,
                actorId, false, Map.of("notifications", Map.of("recipients", six)))).getCode());
        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchStore(storeId,
                actorId, false, Map.of("notifications", Map.of("recipients", List.of("no-es-correo"))))).getCode());
        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchStore(storeId,
                actorId, false, Map.of("profile", Map.of("tagline", "x".repeat(81))))).getCode());

        StoreSettings settings = service.patchStore(storeId, actorId, false,
                Map.of("notifications", Map.of("recipients", List.of("Ventas@Almacen.co", "ventas@almacen.co"))));
        assertEquals(List.of("ventas@almacen.co"), settings.notifications().recipients());
    }

    @Test
    void featuredProductsMustBePublishedAndFromTheStore() {
        UUID published = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(listingQueries.publishedProductIdsAmong(eq(storeId), anyCollection())).thenReturn(Set.of(published));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.patchStore(storeId, actorId,
                false, Map.of("profile", Map.of("featuredProductIds", List.of(published.toString(), foreign.toString())))));
        assertEquals("INVALID_SETTING", error.getCode());
        assertEquals(Map.of("key", "profile.featuredProductIds"), error.getDetails());

        StoreSettings settings = service.patchStore(storeId, actorId, false,
                Map.of("profile", Map.of("featuredProductIds", List.of(published.toString()))));
        assertEquals(List.of(published), settings.profile().featuredProductIds());
    }

    @Test
    void moreThanEightFeaturedProductsIsRejected() {
        List<String> nine = java.util.stream.IntStream.range(0, 9).mapToObj(i -> UUID.randomUUID().toString()).toList();
        assertEquals("INVALID_SETTING", assertThrows(BadRequestException.class, () -> service.patchStore(storeId,
                actorId, false, Map.of("profile", Map.of("featuredProductIds", nine)))).getCode());
    }

    @Test
    void requireStaffTwoFactorNeedsActorWithTwoFactor() {
        Map<String, Object> enable = Map.of("security", Map.of("requireStaffTwoFactor", true));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.patchStore(storeId, actorId, false, enable));
        assertEquals("TWO_FACTOR_REQUIRED_FIRST", error.getCode());

        StoreSettings settings = service.patchStore(storeId, actorId, true, enable);
        assertThat(settings.security().requireStaffTwoFactor()).isTrue();

        // Quien tiene 2FA puede desactivarlo.
        StoreSettings disabled = service.patchStore(storeId, actorId, true,
                Map.of("security", Map.of("requireStaffTwoFactor", false)));
        assertThat(disabled.security().requireStaffTwoFactor()).isFalse();
    }

    @Test
    void actorWithoutTwoFactorCannotPatchWhileStaffTwoFactorIsRequired() {
        store.getConfiguration().put("security", Map.of("requireStaffTwoFactor", true));

        ForbiddenException disable = assertThrows(ForbiddenException.class, () -> service.patchStore(storeId, actorId,
                false, Map.of("security", Map.of("requireStaffTwoFactor", false))));
        assertEquals("STAFF_TWO_FACTOR_REQUIRED", disable.getCode());
        ForbiddenException other = assertThrows(ForbiddenException.class, () -> service.patchStore(storeId, actorId,
                false, Map.of("catalog", Map.of("showExactStock", true))));
        assertEquals("STAFF_TWO_FACTOR_REQUIRED", other.getCode());

        assertThat(service.storeSettings(storeId).security().requireStaffTwoFactor()).isTrue();
        verify(storeRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }
}
