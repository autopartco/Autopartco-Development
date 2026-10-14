package com.autopartco.backend.core.organization;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchLocationResponse(
        UUID id,
        String addressLine,
        String addressComplement,
        String city,
        String department,
        String country,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude
) {
}