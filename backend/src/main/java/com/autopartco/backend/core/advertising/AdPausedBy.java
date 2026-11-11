package com.autopartco.backend.core.advertising;

/**
 * Quién pausó una campaña ({@code ad_campaigns.paused_by}). Solo tiene valor en {@code PAUSED}
 * ({@code (status = 'PAUSED') = (paused_by is not null)}). El almacén no puede reanudar una pausa {@link #ADMIN}.
 */
public enum AdPausedBy {
    STORE,
    ADMIN
}
