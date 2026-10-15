package com.autopartco.backend.storesuite.onboarding;

import java.util.UUID;

import com.autopartco.backend.core.organization.LegalType;
import com.autopartco.backend.core.organization.StoreRegistrationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StoreApplicationRequest(
        @NotNull @Valid StoreRegistrationRequest store,
        @NotNull LegalType legalType,
        UUID planId
) {
}
