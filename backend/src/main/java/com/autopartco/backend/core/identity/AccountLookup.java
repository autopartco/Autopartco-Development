package com.autopartco.backend.core.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Consulta de cuentas para otros módulos. Devuelve {@link AccountRef}, nunca entidades.
 */
@Service
@AllArgsConstructor
public class AccountLookup {
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Optional<AccountRef> findByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(email).map(AccountLookup::toRef);
    }

    @Transactional(readOnly = true)
    public AccountRef requireByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new NotFoundException(
                "ACCOUNT_NOT_FOUND",
                "No se encontró la cuenta autenticada."
        ));
    }

    @Transactional(readOnly = true)
    public Optional<AccountRef> findById(UUID accountId) {
        return accountRepository.findById(accountId).map(AccountLookup::toRef);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return accountRepository.existsByEmailIgnoreCase(email);
    }

    /**
     * Busca la cuenta por correo (sin distinguir mayúsculas) y bloquea su fila ({@code SELECT ... FOR UPDATE})
     * hasta el final de la transacción del llamador.
     */
    @Transactional
    public Optional<AccountRef> lockByEmail(String email) {
        return accountRepository.lockByEmail(email).map(AccountLookup::toRef);
    }

    static AccountRef toRef(Account account) {
        return new AccountRef(
                account.getId(),
                account.getPerson().getId(),
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                account.getState() == null ? null : account.getState().getCode(),
                account.getAccountType().getCode(),
                account.getPerson().getName()
        );
    }
}
