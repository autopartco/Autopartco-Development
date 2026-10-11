package com.autopartco.backend.auth.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PersonAddressResponse(
        UUID id,
        String label,
        boolean isDefault,
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
