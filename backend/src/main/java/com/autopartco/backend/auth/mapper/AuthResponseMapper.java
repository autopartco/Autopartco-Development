package com.autopartco.backend.auth.mapper;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.autopartco.backend.auth.dto.RegisterResponse;
import com.autopartco.backend.auth.dto.VerifyEmailResponse;
import com.autopartco.backend.auth.entity.Account;
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
