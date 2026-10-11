package com.autopartco.backend.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.common.State;
import com.autopartco.backend.exception.NotFoundException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AccountStatusService {
    public static final String ACTIVE = "ACTIVE";
    public static final String LOCKED = "LOCKED";

    private final AccountRepository accountRepository;
    private final StateRepository stateRepository;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account lock(Account account, String ipAddress, int failedAttempts) {
        account.setState(findState(LOCKED));
        Account savedAccount = accountRepository.save(account);
        auditService.recordAccountLocked(savedAccount, ipAddress, failedAttempts);
        return savedAccount;
    }

    @Transactional
    public Account activate(Account account) {
        account.setState(findState(ACTIVE));
        Account savedAccount = accountRepository.save(account);
        auditService.recordAccountUnlocked(savedAccount);
        return savedAccount;
    }

    public boolean isLocked(Account account) {
        return account.getState() != null && LOCKED.equals(account.getState().getCode());
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "STATE_NOT_FOUND",
                        "No existe el estado de cuenta requerido."
                ));
    }
}
