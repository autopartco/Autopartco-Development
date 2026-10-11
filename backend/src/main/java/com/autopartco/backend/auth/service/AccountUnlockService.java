package com.autopartco.backend.auth.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.auth.dto.UnlockAccountRequest;
import com.autopartco.backend.auth.dto.UnlockAccountResponse;
import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountToken;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.config.ApiConfig;
import com.autopartco.backend.config.AppProperties;
import com.autopartco.backend.exception.BadRequestException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AccountUnlockService {
    private static final String REQUEST_MESSAGE = "Si existe una cuenta con ese correo, recibirás un enlace para desbloquearla.";

    private final AccountRepository accountRepository;
    private final EmailVerificationTokenService tokenService;
    private final AccountStatusService accountStatusService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Transactional
    public UnlockAccountResponse requestUnlock(UnlockAccountRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        accountRepository.findByEmailIgnoreCase(email).ifPresent(this::sendUnlockEmail);
        return new UnlockAccountResponse(REQUEST_MESSAGE);
    }

    @Transactional
    public UnlockAccountResponse unlock(String rawToken) {
        AccountToken token = tokenService.findValidAccountUnlockToken(rawToken);
        Account account = token.getAccount();

        if (!accountStatusService.isLocked(account)) {
            throw new BadRequestException(
                "ACCOUNT_NOT_LOCKED",
                "La cuenta no está bloqueada."
            );
        }

        accountStatusService.activate(account);
        token.setUsed(true);
        tokenService.save(token);

        return new UnlockAccountResponse("La cuenta fue desbloqueada correctamente.");
    }

    private void sendUnlockEmail(Account account) {
        EmailVerificationTokenService.CreatedEmailVerificationToken token =
                tokenService.createAccountUnlockToken(account);
        String link = UriComponentsBuilder
                .fromUriString(appProperties.publicBaseUrl())
                .path(ApiConfig.API_BASE_PATH)
                .path("/auth/unlock")
                .queryParam("token", token.rawToken())
                .build()
                .toUriString();

        emailService.sendAccountUnlock(
                account.getEmail(),
                account.getPerson().getName() + " " + account.getPerson().getSurname(),
                link
        );
    }
}
