package com.autopartco.backend.core.billing.internal.wompi;

import com.autopartco.backend.shared.security.TokenHashing;

/**
 * SHA-256 en hexadecimal (minúsculas) que usa Wompi para la firma de integridad del checkout y el checksum de
 * eventos.
 */
public final class WompiHashing {
    private WompiHashing() {
    }

    public static String sha256(String value) {
        return TokenHashing.sha256Hex(value);
    }
}
