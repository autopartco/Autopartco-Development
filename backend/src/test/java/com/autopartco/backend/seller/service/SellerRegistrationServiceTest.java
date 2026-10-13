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
import com.autopartco.backend.store.dto.StoreRegistrationRequest;
import com.autopartco.backend.seller.entity.SellerLegalType;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.seller.repository.SellerPlanRepository;
import com.autopartco.backend.seller.repository.SellerApplicationRepository;
import com.autopartco.backend.seller.repository.SellerApplicationDocumentRepository;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.store.service.StoreService;

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
    private SellerPlanRepository planRepository;

    @Mock
    private SellerPlanService planService;

    @Mock
    private SellerApplicationRepository applicationRepository;

    @Mock
    private SellerApplicationDocumentRepository documentRepository;

    @Mock
    private SellerFileService fileService;

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
                SellerLegalType.NATURAL_PERSON,
                null
        );

        assertThrows(
                BadRequestException.class,
                () -> sellerRegistrationService.register(request, new EnumMap<>(SellerDocumentType.class))
        );
        verify(accountProvisioning, never()).registerSellerAccount(accountRequest);
        verify(storeService, never()).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
