package com.autopartco.backend.storesuite.onboarding;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.EnumMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.RegisterRequest;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.core.organization.StoreRegistrationRequest;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class SubmitStoreApplicationTest {
    @Mock
    private AccountProvisioning accountProvisioning;

    @Mock
    private AccountLookup accountLookup;

    @Mock
    private PlanService planService;

    @Mock
    private StoreApplications storeApplications;

    @InjectMocks
    private SubmitStoreApplication submitStoreApplication;

    @Test
    void rejectsNaturalPersonRegistrationWithoutIdentityAndRutDocuments() {
        RegisterRequest accountRequest = new RegisterRequest(
                "Ana",
                "Pérez",
                "123456",
                LocalDate.of(1995, 4, 20),
                "3001234567",
                "ana@example.com",
                "Autopartco#2026"
        );
        StoreRegistrationRequest storeRequest = new StoreRegistrationRequest(
                "Repuestos Ana",
                "900123456",
                "ventas@example.com"
        );
        var request = new SellerSignupRequest(
                accountRequest,
                storeRequest,
                LegalType.NATURAL_PERSON,
                null
        );

        assertThrows(
                BadRequestException.class,
                () -> submitStoreApplication.register(request, new EnumMap<>(ApplicationDocumentType.class))
        );
        verify(accountProvisioning, never()).registerSellerAccount(accountRequest);
        verify(storeApplications, never()).submit(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(accountLookup, planService);
    }
}
