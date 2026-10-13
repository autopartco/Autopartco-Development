package com.autopartco.backend.core.identity.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.web.VerifyEmailResponse;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmailVerificationService {
    private static final String VERIFIED_STATE = StateCodes.ACTIVE;

    private final AccountRepository accountRepository;
    private final StateRepository stateRepository;
    private final EmailVerificationTokenService tokenService;
    private final AuditService auditService;
    private final AuthResponseMapper authResponseMapper;

    @Transactional
    public VerifyEmailResponse verify(String rawToken) {
        var accountToken = tokenService.findValidVerificationToken(rawToken);

        State activeState = stateRepository.findByCode(VERIFIED_STATE)
                .orElseThrow(() -> new NotFoundException("STATE_NOT_FOUND", "No existe el estado activo."));

        Account account = accountToken.getAccount();
        account.setState(activeState);
        accountToken.setUsed(true);

        accountRepository.save(account);
        tokenService.save(accountToken);
        auditService.recordEmailVerified(account.getId(), account.getEmail(), activeState.getCode());

        return authResponseMapper.toVerifyEmailResponse(account, activeState);
    }
}
