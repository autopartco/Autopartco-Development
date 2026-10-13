package com.autopartco.backend.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.entity.Account;
import com.autopartco.backend.auth.entity.AccountSession;
import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.AccountSessionRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AccountSessionService {
    private final AccountSessionRepository repository;
    private final AccountRepository accountRepository;

    @Transactional
    public AccountSession create(
            java.util.UUID sessionId,
            Account account,
            String refreshToken,
            String ipAddress,
            String userAgent,
            LocalDateTime expiredAt
    ) {
        int insertedRows = repository.insertSession(
                sessionId,
                account.getId(),
                RefreshTokenHash.of(refreshToken),
                ipAddress,
                userAgent,
                expiredAt
        );
        if (insertedRows != 1) {
            throw new IllegalStateException("Account session insert did not affect exactly one row.");
        }
        return repository.findById(sessionId).orElseThrow(() -> new IllegalStateException(
                "Account session insert did not create the expected session."
        ));
    }

    @Transactional(readOnly = true)
    public List<AccountSession> findActiveByAccountId(UUID accountId) {
        return repository.findActiveByAccountId(
                accountId,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<AccountSession> findActiveByAccountEmail(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException(
                        "ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta autenticada."
                ));
        return findActiveByAccountId(account.getId());
    }

    @Transactional
    public void revoke(UUID sessionId) {
        repository.findById(sessionId).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            repository.save(session);
        });
    }

    @Transactional
    public void revokeByRefreshToken(String refreshToken, String accountEmail) {
        repository.findByRefreshTokenHashForUpdate(RefreshTokenHash.of(refreshToken))
                .filter(session -> session.getAccount().getEmail().equalsIgnoreCase(accountEmail))
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    repository.save(session);
                });
    }

    @Transactional
    public void revokeOthersByRefreshToken(String currentRefreshToken, String accountEmail) {
        repository.findByRefreshTokenHashForUpdate(RefreshTokenHash.of(currentRefreshToken))
                .filter(currentSession -> currentSession.getAccount().getEmail().equalsIgnoreCase(accountEmail))
                .ifPresent(currentSession -> repository
                        .findNonRevokedByAccountId(currentSession.getAccount().getId())
                        .stream()
                        .filter(session -> !session.getId().equals(currentSession.getId()))
                        .forEach(session -> {
                            session.setRevokedAt(LocalDateTime.now());
                            repository.save(session);
                        }));
    }

    @Transactional
    public void revokeAll(UUID accountId) {
        repository.findNonRevokedByAccountId(accountId).forEach(session -> {
            session.setRevokedAt(LocalDateTime.now());
            repository.save(session);
        });
    }

    @Transactional
    public void revokeOwnedSession(UUID sessionId, String accountEmail) {
        repository.findById(sessionId)
                .filter(session -> session.getAccount().getEmail().equalsIgnoreCase(accountEmail))
                .ifPresentOrElse(session -> {
                    if (session.getRevokedAt() == null) {
                        session.setRevokedAt(LocalDateTime.now());
                        repository.save(session);
                    }
                }, () -> {
                    throw new NotFoundException("SESSION_NOT_FOUND", "No se encontró la sesión.");
                });
    }
}