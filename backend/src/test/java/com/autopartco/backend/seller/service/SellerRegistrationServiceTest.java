package com.autopartco.backend.seller.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.EnumMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.AccountProvisioning;
import com.autopartco.backend.core.identity.RegisterRequest;
import com.autopartco.backend.seller.dto.SellerRegistrationRequest;
import com.autopartco.backend.core.organization.StoreRegistrationRequest;
import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.core.organization.ApplicationDocumentType;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.core.organization.StoreService;

@ExtendWith(MockitoExtension.class)
class SellerRegistrationServiceTest {
    @Mock
    private AccountProvisioning accountProvisioning;

    @Mock
    private StoreService storeService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private PlanService planService;

    @Mock
    private StoreApplicationRepository applicationRepository;

    @Mock
    private StoreApplicationDocumentRepository documentRepository;

    @Mock
    private StoreFileStorage fileService;

    @InjectMocks
    private SellerRegistrationService sellerRegistrationService;

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
        var request = new SellerRegistrationRequest(
                accountRequest,
                storeRequest,
                LegalType.NATURAL_PERSON,
                null
        );

        assertThrows(
                BadRequestException.class,
                () -> sellerRegistrationService.register(request, new EnumMap<>(ApplicationDocumentType.class))
        );
        verify(accountProvisioning, never()).registerSellerAccount(accountRequest);
        verify(storeService, never()).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
