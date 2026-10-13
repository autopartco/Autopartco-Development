package com.autopartco.backend.core.identity.internal;

import com.autopartco.backend.shared.security.TokenHashing;

/** Hash con el que se guarda el refresh token en {@code account_sessions.refresh_token_hash}. */
public final class RefreshTokenHash {
    private RefreshTokenHash() {
    }

    public static String of(String rawToken) {
        return TokenHashing.sha256Hex(rawToken);
    }
}
