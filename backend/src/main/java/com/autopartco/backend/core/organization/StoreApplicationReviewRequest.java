package com.autopartco.backend.core.organization;

import jakarta.validation.constraints.Size;

/** Decisión de root sobre una solicitud de almacén pendiente. */
public record StoreApplicationReviewRequest(
        boolean approved,
        @Size(max = 2000) String notes
) {
}
