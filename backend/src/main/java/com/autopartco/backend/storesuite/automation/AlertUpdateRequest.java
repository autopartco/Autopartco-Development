package com.autopartco.backend.storesuite.automation;

/** Cambio de estado de una alerta: {@code {"read": true}} o {@code {"resolved": true}} (resolver implica leída). */
public record AlertUpdateRequest(Boolean read, Boolean resolved) {
    boolean markRead() {
        return Boolean.TRUE.equals(read);
    }

    boolean markResolved() {
        return Boolean.TRUE.equals(resolved);
    }
}
