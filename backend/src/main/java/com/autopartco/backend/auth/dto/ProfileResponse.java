package com.autopartco.backend.auth.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID accountId,
        UUID personId,
        String email,
        String name,
        String surname,
        String identification,
        String dateBirth,
        String numberPhone
) {
}