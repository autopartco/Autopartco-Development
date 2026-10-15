package com.autopartco.backend.core.identity;

import java.math.BigDecimal;

/** Copia de una dirección guardada de la persona, para congelarla en un pedido. Sin identificadores internos. */
public record AddressSnapshot(
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
