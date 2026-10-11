package com.autopartco.backend.auth.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.dto.PasswordRecoveryRequest;
import com.autopartco.backend.auth.dto.PasswordRecoveryResponse;
import com.autopartco.backend.auth.dto.PasswordResetRequest;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountToken;
import com.autopartco.backend.auth.repository.AccountRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PasswordRecoveryService {
    private static final String REQUEST_MESSAGE =
            "Si existe una cuenta con ese correo, recibirás instrucciones para recuperar tu contraseña.";

    private final AccountRepository accountRepository;
    private final EmailVerificationTokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionService accountSessionService;

    @Transactional
    public PasswordRecoveryResponse requestRecovery(PasswordRecoveryRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        accountRepository.findByEmailIgnoreCase(email).ifPresent(this::sendRecoveryEmail);
        return new PasswordRecoveryResponse(REQUEST_MESSAGE);
    }

    @Transactional
    public PasswordRecoveryResponse resetPassword(PasswordResetRequest request) {
        AccountToken token = tokenService.findValidPasswordRecoveryToken(request.token());
        Account account = token.getAccount();
        account.setPassword(passwordEncoder.encode(request.password()));
        token.setUsed(true);
        tokenService.save(token);
        accountSessionService.revokeAll(account.getId());

        return new PasswordRecoveryResponse("La contraseña fue actualizada correctamente.");
    }

    private void sendRecoveryEmail(Account account) {
        EmailVerificationTokenService.CreatedEmailVerificationToken token =
                tokenService.createPasswordRecoveryToken(account);
        emailService.sendPasswordRecovery(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                token.rawToken(),
                token.expiresAt()
        );
    }
}