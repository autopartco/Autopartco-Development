package com.autopartco.backend.core.identity;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.core.identity.internal.AccountType;
import com.autopartco.backend.core.identity.internal.AccountTypeRepository;
import com.autopartco.backend.core.identity.internal.RegistrationService;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Alta y cambios de tipo de cuenta pedidos por otros módulos (registro de vendedor, alta de almacén).
 */
@Service
@AllArgsConstructor
public class AccountProvisioning {
    private static final String STORE_MANAGER_TYPE = "STORE_MANAGER";

    private final RegistrationService registrationService;
    private final AccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;

    /** Crea persona y cuenta pendientes sin enviar el correo de verificación. */
    @Transactional
    public PreparedRegistration registerSellerAccount(RegisterRequest request) {
        return registrationService.registerSellerAccount(request);
    }

    public void sendSellerVerification(PreparedRegistration prepared) {
        registrationService.sendSellerVerification(prepared);
    }

    /** Cambia la cuenta al tipo {@code STORE_MANAGER} dentro de la transacción del llamador. */
    @Transactional
    public void promoteToStoreManager(UUID accountId) {
        AccountType storeManager = accountTypeRepository.findByCode(STORE_MANAGER_TYPE)
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_TYPE_NOT_FOUND",
                        "No existe el tipo de cuenta de vendedor."
                ));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta."));
        account.setAccountType(storeManager);
    }
}
