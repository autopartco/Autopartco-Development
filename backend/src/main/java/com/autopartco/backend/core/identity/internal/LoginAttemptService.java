package com.autopartco.backend.core.identity.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
public class LoginAttemptService {
    static final int MAX_FAILED_ATTEMPTS = 3;

    private final LoginAttemptRepository repository;
    private final LoginAttemptMapper loginAttemptMapper;

    public LoginAttemptService(LoginAttemptRepository repository, LoginAttemptMapper loginAttemptMapper) {
        this.repository = repository;
        this.loginAttemptMapper = loginAttemptMapper;
    }

    public List<LoginAttempt> getLastAttempts(UUID accountId, int limit) {
        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by("attemptedAt").descending()
        );

        return repository.findByAccountId(accountId, pageable);
    }

    public boolean hasExceededFailedAttempts(UUID accountId) {
        List<LoginAttempt> attempts = getLastAttempts(accountId, MAX_FAILED_ATTEMPTS);

        if (attempts.size() < MAX_FAILED_ATTEMPTS) {
            return false;
        }

        String lastIp = attempts.get(0).getIpAddress();

        return attempts.stream().allMatch(attempt ->
                        !attempt.isSuccessful() && lastIp.equals(attempt.getIpAddress())
                );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginAttempt record(Account account, boolean successful, String ipAddress) {
        return repository.save(loginAttemptMapper.toLoginAttempt(account, successful, ipAddress));
    }

}
