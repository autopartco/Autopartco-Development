package com.autopartco.backend.storesuite.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /store-applications/setup/resend}: el token del enlace anterior (vencido o no). */
public record SetupLinkResendRequest(@NotBlank @Size(max = 200) String token) {
}
