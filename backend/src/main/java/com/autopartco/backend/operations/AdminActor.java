package com.autopartco.backend.operations;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.identity.AccountLookup;

import lombok.AllArgsConstructor;

/** Quién actúa en la consola: su cuenta y si es {@code ROOT}. */
@Component
@AllArgsConstructor
public class AdminActor {
    private final AccountLookup accountLookup;

    public record Actor(UUID accountId, String email, boolean root) {
    }

    public Actor of(Authentication authentication) {
        UUID accountId = accountLookup.requireByEmail(authentication.getName()).accountId();
        boolean root = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ROOT".equals(a.getAuthority()));
        return new Actor(accountId, authentication.getName(), root);
    }
}
