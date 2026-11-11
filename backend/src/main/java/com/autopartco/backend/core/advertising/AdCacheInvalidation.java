package com.autopartco.backend.core.advertising;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Vacía la caché de candidatos al confirmar la transacción en curso (o de inmediato si no hay una). */
public final class AdCacheInvalidation {
    private AdCacheInvalidation() {
    }

    public static void afterCommit(AdCandidateCache cache) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidate();
                }
            });
        } else {
            cache.invalidate();
        }
    }
}
