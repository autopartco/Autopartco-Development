package com.autopartco.backend.core.identity.internal;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.identity.RegisterResponse;
import com.autopartco.backend.core.identity.web.VerifyEmailResponse;
import com.autopartco.backend.shared.persistence.State;

@Component
public class AuthResponseMapper {

    public RegisterResponse toRegisterResponse(Account account, LocalDateTime verificationTokenExpiresAt) {
        return new RegisterResponse(
                account.getId(),
                account.getPerson().getId(),
                account.getEmail(),
                account.getAccountType().getCode(),
                account.getState().getCode(),
                true,
                verificationTokenExpiresAt
        );
    }

    public VerifyEmailResponse toVerifyEmailResponse(Account account, State state) {
        return new VerifyEmailResponse(
                account.getId(),
                account.getEmail(),
                state.getCode(),
                "Correo verificado correctamente."
        );
    }
}
