package com.autopartco.backend.core.identity.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.core.audit.AuditService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AccountStatusService {

    private final AccountRepository accountRepository;
    private final StateRepository stateRepository;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account lock(Account account, String ipAddress, int failedAttempts) {
        account.setState(findState(StateCodes.LOCKED));
        Account savedAccount = accountRepository.save(account);
        auditService.recordAccountLocked(savedAccount.getId(), savedAccount.getEmail(), ipAddress, failedAttempts, savedAccount.getState().getCode());
        return savedAccount;
    }

    @Transactional
    public Account activate(Account account) {
        account.setState(findState(StateCodes.ACTIVE));
        Account savedAccount = accountRepository.save(account);
        auditService.recordAccountUnlocked(savedAccount.getId(), savedAccount.getEmail(), savedAccount.getState().getCode());
        return savedAccount;
    }

    public boolean isLocked(Account account) {
        return account.getState() != null && StateCodes.LOCKED.equals(account.getState().getCode());
    }

    private State findState(String code) {
        return stateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "STATE_NOT_FOUND",
                        "No existe el estado de cuenta requerido."
                ));
    }
}
